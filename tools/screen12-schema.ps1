#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$RepositoryRoot='',
    [Parameter(Mandatory=$true)][string]$DatabaseContainer,
    [string]$Database='',
    [string]$DatabaseUser='postgres',
    [ValidateSet('TestFresh','Apply','Verify')][string]$Mode='TestFresh'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version 2.0
if(-not $RepositoryRoot) {
    if(-not $PSScriptRoot) {throw 'Supply RepositoryRoot when loading this runner as a scriptblock.'}
    $RepositoryRoot=Split-Path $PSScriptRoot -Parent
}
$RepositoryRoot=(Get-Item -LiteralPath $RepositoryRoot).FullName.TrimEnd('\','/')
$manifest=ConvertFrom-Json -InputObject ([IO.File]::ReadAllText((Join-Path $RepositoryRoot 'docs\db\schema-manifest.json')))
if(-not $Database) {$Database=[string]$manifest.DefaultDatabase}
if($Database -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}$' -or $DatabaseUser -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}$') {throw 'Database and role must be plain identifiers; connection URLs are not accepted.'}
if($DatabaseContainer -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') {throw 'Invalid database container name/ID.'}
foreach($item in $manifest.Files) {
    $file=[IO.Path]::GetFullPath((Join-Path $RepositoryRoot ([string]$item.Path).Replace('/','\')))
    if(-not $file.StartsWith($RepositoryRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or
        -not(Test-Path -LiteralPath $file -PathType Leaf) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $item.SHA256) {
        throw "Missing/edited schema file: $($item.Path). Create a new migration version for future changes."
    }
}
$javaRoot=Join-Path $RepositoryRoot 'src\main\java'
if(Test-Path -LiteralPath $javaRoot -PathType Container) {
    foreach($mapping in $manifest.EntityMappings) {
        $sourcePath=Join-Path $RepositoryRoot ([string]$mapping.Source).Replace('/','\')
        if(-not(Test-Path -LiteralPath $sourcePath -PathType Leaf)) {throw "Missing entity source for schema mapping: $($mapping.Source)"}
        $source=[IO.File]::ReadAllText($sourcePath)
        $tableMatch=[regex]::Match($source,'(?s)@Table\s*\([^)]*\bname\s*=\s*"([^"]+)"')
        if(-not $tableMatch.Success -or $tableMatch.Groups[1].Value -cne $mapping.Table) {throw "JPA table mapping differs from SQL manifest: $($mapping.Source)"}
    }
}
$docker=(Get-Command docker -ErrorAction Stop).Source
function Native([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $lines=@(& $docker @Arguments 2>&1)
        $code=$LASTEXITCODE
        [pscustomobject]@{Code=$code;Lines=@($lines | ForEach-Object {[string]$_})}
    } finally {$ErrorActionPreference=$previousPreference}
}
$contextReply=Native @('context','show')
$context=($contextReply.Lines -join '').Trim()
if($contextReply.Code -ne 0 -or $context -notmatch '^[A-Za-z0-9_.-]+$') {throw 'Cannot pin Docker context.'}
$endpointReply=Native @('context','inspect','--format','{{.Endpoints.docker.Host}}',$context)
$endpoint=($endpointReply.Lines -join '').Trim()
if($endpointReply.Code -ne 0 -or $endpoint -notmatch '^(npipe://|unix://|tcp://(127\.0\.0\.1|localhost|\[::1\]):)' -or
    ($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^(npipe://|unix://|tcp://(127\.0\.0\.1|localhost|\[::1\]):)')) {throw 'This Docker runner only operates on a local engine. For VPS, use the repository psql/Python runner with its existing connection configuration.'}
$stateReply=Native @('--context',$context,'inspect','--type','container','--format','{{.Id}}|{{json .State.Running}}',$DatabaseContainer)
$parts=($stateReply.Lines -join '').Trim().Split('|')
if($stateReply.Code -ne 0 -or $parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9a-f]{64}$' -or $parts[1] -cne 'true') {throw 'The selected local PostgreSQL container must be running.'}
$containerId=$parts[0]
$checks=[Collections.Generic.List[object]]::new()
$failure=$null;$tempCreated=$false;$tempDatabase=''
$token=[guid]::NewGuid().ToString('N').Substring(0,12)
$containerDir='/tmp/dts12_schema_'+$token
$reportRoot=Join-Path $RepositoryRoot ('build\screen12-schema-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+$token)
[IO.Directory]::CreateDirectory($reportRoot) | Out-Null
function Check([string]$Name,[bool]$Passed,[string]$Details) {
    $checks.Add([pscustomobject]@{Case=$Name;Passed=$Passed;Details=$Details})
    Write-Host ("{0}: {1} ({2})" -f $Name,$(if($Passed){'PASS'}else{'FAIL'}),$Details)
    if(-not $Passed) {throw "Schema check failed: $Name"}
}
function PsqlCommand([string]$Target,[string[]]$Extra) {
    $arguments=@('--context',$context,'exec')
    if($env:PGPASSWORD) {$arguments+=@('-e','PGPASSWORD')}
    $arguments+=@($containerId,'psql','-X','-w','-q','-t','-A','-U',$DatabaseUser,'-d',$Target,'-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate')+$Extra
    Native $arguments
}
function RunFile([string]$Target,[string]$File,[string]$ExpectedSqlState='') {
    $result=PsqlCommand $Target @('-v',('expected_database='+$Target),'-f',($containerDir+'/db/'+$File))
    $stateMatch=[regex]::Match(($result.Lines -join "`n"),'(?:ERROR|FATAL):\s*([0-9A-Z]{5})')
    $state=if($stateMatch.Success){$stateMatch.Groups[1].Value}else{'UNKNOWN'}
    if($ExpectedSqlState) {return $result.Code -eq 3 -and $state -ceq $ExpectedSqlState}
    if($result.Code -ne 0) {
        # No arbitrary database row, password or stored snapshot is copied into the report.
        throw "psql failed in $File (exit=$($result.Code); SQLState=$state). Migration transaction must roll back; review this file and the database schema before retrying."
    }
    $true
}
try {
    $copy=Native @('--context',$context,'cp',(Join-Path $RepositoryRoot 'docs\db'),($containerId+':'+$containerDir))
    Check 'CopyCheckedSchemaFiles' ($copy.Code -eq 0) 'Checked-in SQL copied to the selected local database container'
    # Docker cp of a directory to a nonexistent target copies its CONTENTS into that target.
    # Use a stable directory containing a db child for relative psql includes.
    $layout=Native @('--context',$context,'exec',$containerId,'sh','-c',('test -f '+$containerDir+'/db/apply-schema.psql'))
    if($layout.Code -ne 0) {
        # Move only this unique newly copied directory; no shared path is changed.
        $fix=Native @('--context',$context,'exec',$containerId,'sh','-c',('mv '+$containerDir+' '+$containerDir+'_files && mkdir '+$containerDir+' && mv '+$containerDir+'_files '+$containerDir+'/db'))
        Check 'SchemaDirectoryLayout' ($fix.Code -eq 0) 'Relative migration includes resolved'
    }
    if($Mode -eq 'TestFresh') {
        $tempDatabase='dts12_schema_test_'+(Get-Date -Format 'yyyyMMddHHmmss')+'_'+$token
        $create=PsqlCommand 'postgres' @('-c',('CREATE DATABASE "'+$tempDatabase+'"'))
        Check 'CreateOwnedTemporaryDatabase' ($create.Code -eq 0) $tempDatabase
        $tempCreated=$true
        $wrongTarget=PsqlCommand $tempDatabase @('-v',('expected_database='+$Database),'-f',($containerDir+'/db/apply-schema.psql'))
        Check 'WrongTargetDatabaseRefused' ($wrongTarget.Code -eq 3 -and [regex]::IsMatch(($wrongTarget.Lines -join "`n"),'(?:ERROR|FATAL):\s*P0001\b')) 'Expected database guard stops before schema creation'
        Check 'FreshDatabaseMigration' (RunFile $tempDatabase 'apply-schema.psql') 'All entity tables, columns, constraints, indexes and triggers created from repository files'
        Check 'FreshSchemaContractSmoke' (RunFile $tempDatabase 'tests/schema-smoke.sql') 'Required Screen12 schema behaviors and rejected invalid rows verified; fixture transaction rolled back'
        Check 'RepeatMigration' (RunFile $tempDatabase 'apply-schema.psql') 'Executed versions are skipped; checksums and schema verified'
        Check 'PrepareExistingJpaCriteria' (RunFile $tempDatabase 'tests/jpa-criteria-fixture.sql') 'Real JPA table exam_criterias populated and linked to a version before any recorded migration'
        Check 'MigrateExistingJpaCriteria' (RunFile $tempDatabase 'apply-schema.psql') 'Baseline reuses canonical JPA criteria table and validates existing references'
        Check 'ExistingJpaCriteriaPreserved' (RunFile $tempDatabase 'tests/verify-jpa-criteria.sql') 'Existing criteria, exam, structure, rule and version row hashes preserved; FK targets the JPA table'
        Check 'PrepareLegacySchemaAndRecord' (RunFile $tempDatabase 'tests/legacy-fixture.sql') 'Legacy schema/data fixture exists only in the owned temporary database'
        Check 'UpgradeLegacySchema' (RunFile $tempDatabase 'apply-schema.psql') 'Stored version applied to legacy schema'
        Check 'ExistingRecordPreserved' (RunFile $tempDatabase 'tests/verify-preserved.sql') 'Complete JSON row hash and version history unchanged except the expected new migration record'
        Check 'UpgradedSchemaContractSmoke' (RunFile $tempDatabase 'tests/schema-smoke.sql') 'Same contract works after legacy upgrade'
        Check 'PreparePriorV002Default' (RunFile $tempDatabase 'tests/v002-default-fixture.sql') 'Executed V001/V002 plus legacy ACTIVE default simulated only in the temporary database'
        Check 'LegacyDefaultDriftDetected' (RunFile $tempDatabase 'verify-schema.sql' -ExpectedSqlState 'P0001') 'Read-only schema verification refuses ACTIVE as the default for future structures'
        Check 'UpgradeV002WithV003' (RunFile $tempDatabase 'apply-schema.psql') 'V003 restores DRAFT without replaying V001/V002'
        Check 'V003PreservesRecordsAndHistory' (RunFile $tempDatabase 'tests/verify-v003-upgrade.sql') 'Whole-row hashes of existing exam, ACTIVE structure and executed V001/V002 history preserved'
        Check 'V003UpgradeContractSmoke' (RunFile $tempDatabase 'tests/schema-smoke.sql') 'New structures default to DRAFT after upgrading an already-versioned database'
        Check 'V003RepeatMigration' (RunFile $tempDatabase 'apply-schema.psql') 'All three executed versions are skipped and the corrected schema is verified'
        Check 'PrepareIncompatibleLegacyData' (RunFile $tempDatabase 'tests/invalid-legacy-fixture.sql') 'Incompatible data fixture is confined to the temporary database'
        $conflictState=if($manifest.Service -eq 'dts-examination'){'23514'}else{'23505'}
        Check 'IncompatibleDataRefused' (RunFile $tempDatabase 'apply-schema.psql' -ExpectedSqlState $conflictState) "Expected PostgreSQL SQLState=$conflictState; incompatible data is preserved"
        Check 'FailedMigrationRolledBack' (RunFile $tempDatabase 'tests/verify-rollback.sql') 'Legacy data and unapplied-version state preserved on failure'
        Check 'PrepareChangedHistoryChecksum' (RunFile $tempDatabase 'tests/checksum-fixture.sql') 'Checksum tamper fixture is confined to temporary history'
        Check 'ChangedExecutedMigrationRefused' (RunFile $tempDatabase 'apply-schema.psql' -ExpectedSqlState 'P0001') 'Executed migration content mismatch rejected by the version guard'
        Check 'ChangedChecksumNotOverwritten' (RunFile $tempDatabase 'tests/verify-checksum-refusal.sql') 'Runner never silently replaces executed history'
    } elseif($Mode -eq 'Apply') {
        Check 'ApplyVersionedSchema' (RunFile $Database 'apply-schema.psql') 'Repository versions committed atomically to the explicitly selected existing service database'
        Check 'PostMigrationSchemaVerified' (RunFile $Database 'verify-schema.sql') 'Column, primary key, checks, index and trigger contract verified'
    } else {
        $verify=PsqlCommand $Database @('-c','BEGIN READ ONLY;','-f',($containerDir+'/db/verify-schema.sql'),'-c','COMMIT;')
        Check 'ReadOnlySchemaVerified' ($verify.Code -eq 0) 'No schema or business row changed'
    }
} catch {
    $failure=$_.Exception.Message
    if(@($checks | Where-Object {-not $_.Passed}).Count -eq 0) {$checks.Add([pscustomobject]@{Case='SchemaRunnerInterrupted';Passed=$false;Details=$failure})}
    Write-Host $failure -ForegroundColor Red
} finally {
    if($tempCreated) {
        $drop=PsqlCommand 'postgres' @('-c',('DROP DATABASE "'+$tempDatabase+'"'))
        $checks.Add([pscustomobject]@{Case='DropOwnedTemporaryDatabase';Passed=($drop.Code -eq 0);Details=$tempDatabase})
        if($drop.Code -ne 0) {$failure='Owned temporary database cleanup failed: '+$tempDatabase;Write-Host $failure -ForegroundColor Red}
    }
    $cleanup=Native @('--context',$context,'exec',$containerId,'rm','-rf',$containerDir,($containerDir+'_files'))
    $checks.Add([pscustomobject]@{Case='RemoveOwnedTemporarySqlCopy';Passed=($cleanup.Code -eq 0);Details='Only the unique SQL copy created by this run'})
    $passed=-not $failure -and @($checks | Where-Object {-not $_.Passed}).Count -eq 0
    $report=[ordered]@{Service=$manifest.Service;Mode=$Mode;Database=$Database;TemporaryTestDatabase=$tempDatabase;DockerContext=$context;ContainerId=$containerId;Passed=$passed;Failure=$failure;Checks=@($checks.ToArray());RuntimeHttpKafkaVerified=$false;FinishedAt=[DateTimeOffset]::UtcNow.ToString('o')}
    $reportPath=Join-Path $reportRoot 'report.json'
    [IO.File]::WriteAllText($reportPath,(ConvertTo-Json -InputObject $report -Depth 20),[Text.UTF8Encoding]::new($false))
    $checks | Format-Table -AutoSize
    [pscustomobject]@{SchemaMode=$Mode;Service=$manifest.Service;SchemaChecksPassed=$passed;FailedChecks=@($checks | Where-Object {-not $_.Passed}).Count;ReportPath=$reportPath} | Format-List
}
if(-not $passed) {throw "Schema $Mode did not pass. Send report.json at $reportPath before applying further changes."}
