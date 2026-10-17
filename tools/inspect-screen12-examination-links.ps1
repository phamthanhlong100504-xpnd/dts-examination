#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$DtsRoot='D:\DTS',
    [Parameter(Mandatory=$true)][string]$DatabaseContainer,
    [string]$Database='examination',
    [string]$DatabaseUser='postgres',
    [ValidateRange(1,50)][int]$SampleLimit=10,
    [switch]$PassThru
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version 2.0
$DtsRoot=(Get-Item -LiteralPath $DtsRoot).FullName.TrimEnd('\','/')
if($Database -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}$' -or $DatabaseUser -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}$') {throw 'Database and role must be plain identifiers.'}
if($DatabaseContainer -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') {throw 'Invalid container name/ID.'}
$docker=(Get-Command docker -ErrorAction Stop).Source
function InvokeDocker([string[]]$Arguments,[string]$InputSql='') {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        if($InputSql) {$lines=@($InputSql | & $docker @Arguments 2>&1)}
        else {$lines=@(& $docker @Arguments 2>&1)}
        $code=$LASTEXITCODE
        [pscustomobject]@{Code=$code;Lines=@($lines | ForEach-Object {[string]$_})}
    } finally {$ErrorActionPreference=$previousPreference}
}
$reply=InvokeDocker @('context','show')
$context=($reply.Lines -join '').Trim()
if($reply.Code -ne 0 -or $context -notmatch '^[A-Za-z0-9_.-]+$') {throw 'Cannot pin Docker context.'}
$reply=InvokeDocker @('context','inspect','--format','{{.Endpoints.docker.Host}}',$context)
$endpoint=($reply.Lines -join '').Trim()
if($reply.Code -ne 0 -or $endpoint -notmatch '^(npipe://|unix://|tcp://(127\.0\.0\.1|localhost|\[::1\]):)' -or
    ($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^(npipe://|unix://|tcp://(127\.0\.0\.1|localhost|\[::1\]):)')) {throw 'Select the local Docker engine used for the examination database.'}
$reply=InvokeDocker @('--context',$context,'inspect','--type','container','--format','{{.Id}}|{{json .State.Running}}',$DatabaseContainer)
$parts=($reply.Lines -join '').Trim().Split('|')
if($reply.Code -ne 0 -or $parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9a-f]{64}$' -or $parts[1] -cne 'true') {throw 'The selected PostgreSQL container must be running.'}
$containerId=$parts[0]
$sql=@'
\set ON_ERROR_STOP on
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL search_path=public,pg_catalog;
SET LOCAL statement_timeout='30s';
SET LOCAL lock_timeout='5s';
SELECT set_config('dts.expected_database', :'expected_database', true);
DO $scope$
BEGIN
 IF current_database()<>current_setting('dts.expected_database') OR
    current_setting('transaction_read_only')<>'on' THEN
  RAISE EXCEPTION 'Read-only diagnostic database scope check failed';
 END IF;
END;
$scope$;
SELECT json_build_object('kind','scope','database',current_database(),
 'schema',current_schema(),'readOnly',current_setting('transaction_read_only')='on',
 'serverVersion',current_setting('server_version_num'),
 'canonicalCriteriaTablePresent',to_regclass('public.exam_criterias') IS NOT NULL,
 'legacySingularCriteriaTablePresent',to_regclass('public.exam_criteria') IS NOT NULL);
SELECT to_regclass('public.dts_examination_schema_history') IS NOT NULL AS has_history \gset
\if :has_history
SELECT json_build_object('kind','history','present',true,'versions',
 COALESCE((SELECT json_agg(h ORDER BY h.version) FROM
  (SELECT version,script,checksum FROM public.dts_examination_schema_history) h),'[]'::json));
\else
SELECT json_build_object('kind','history','present',false,'versions','[]'::json);
\endif

SELECT (
 EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='exam_id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exams') AND attname='id' AND attnum>0 AND NOT attisdropped)
) AS link_0_available \gset
\if :link_0_available
WITH missing AS MATERIALIZED (
 SELECT c.id AS child_id,c.exam_id AS missing_parent_id
 FROM public.exam_versions c
 LEFT JOIN public.exams p ON p.id=c.exam_id
 WHERE c.exam_id IS NOT NULL AND p.id IS NULL
)
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_id -> exams.id',
 'childTable','exam_versions','referenceColumn','exam_id','parentTable','exams',
 'schemaAvailable',true,'orphanRows',(SELECT count(*) FROM missing),
 'samples',COALESCE((SELECT json_agg(s) FROM
  (SELECT child_id AS "childId",missing_parent_id AS "missingParentId"
   FROM missing ORDER BY child_id LIMIT __SAMPLE_LIMIT__) s),'[]'::json));
\else
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_id -> exams.id',
 'childTable','exam_versions','referenceColumn','exam_id','parentTable','exams',
 'schemaAvailable',false,'orphanRows',NULL,'samples','[]'::json);
\endif

SELECT (
 EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='exam_structure_id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_structures') AND attname='id' AND attnum>0 AND NOT attisdropped)
) AS link_1_available \gset
\if :link_1_available
WITH missing AS MATERIALIZED (
 SELECT c.id AS child_id,c.exam_structure_id AS missing_parent_id
 FROM public.exam_versions c
 LEFT JOIN public.exam_structures p ON p.id=c.exam_structure_id
 WHERE c.exam_structure_id IS NOT NULL AND p.id IS NULL
)
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_structure_id -> exam_structures.id',
 'childTable','exam_versions','referenceColumn','exam_structure_id','parentTable','exam_structures',
 'schemaAvailable',true,'orphanRows',(SELECT count(*) FROM missing),
 'samples',COALESCE((SELECT json_agg(s) FROM
  (SELECT child_id AS "childId",missing_parent_id AS "missingParentId"
   FROM missing ORDER BY child_id LIMIT __SAMPLE_LIMIT__) s),'[]'::json));
\else
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_structure_id -> exam_structures.id',
 'childTable','exam_versions','referenceColumn','exam_structure_id','parentTable','exam_structures',
 'schemaAvailable',false,'orphanRows',NULL,'samples','[]'::json);
\endif

SELECT (
 EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='exam_rule_id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_rules') AND attname='id' AND attnum>0 AND NOT attisdropped)
) AS link_2_available \gset
\if :link_2_available
WITH missing AS MATERIALIZED (
 SELECT c.id AS child_id,c.exam_rule_id AS missing_parent_id
 FROM public.exam_versions c
 LEFT JOIN public.exam_rules p ON p.id=c.exam_rule_id
 WHERE c.exam_rule_id IS NOT NULL AND p.id IS NULL
)
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_rule_id -> exam_rules.id',
 'childTable','exam_versions','referenceColumn','exam_rule_id','parentTable','exam_rules',
 'schemaAvailable',true,'orphanRows',(SELECT count(*) FROM missing),
 'samples',COALESCE((SELECT json_agg(s) FROM
  (SELECT child_id AS "childId",missing_parent_id AS "missingParentId"
   FROM missing ORDER BY child_id LIMIT __SAMPLE_LIMIT__) s),'[]'::json));
\else
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_rule_id -> exam_rules.id',
 'childTable','exam_versions','referenceColumn','exam_rule_id','parentTable','exam_rules',
 'schemaAvailable',false,'orphanRows',NULL,'samples','[]'::json);
\endif

SELECT (
 EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='exam_criteria_id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_criterias') AND attname='id' AND attnum>0 AND NOT attisdropped)
) AS link_3_available \gset
\if :link_3_available
WITH missing AS MATERIALIZED (
 SELECT c.id AS child_id,c.exam_criteria_id AS missing_parent_id
 FROM public.exam_versions c
 LEFT JOIN public.exam_criterias p ON p.id=c.exam_criteria_id
 WHERE c.exam_criteria_id IS NOT NULL AND p.id IS NULL
)
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_criteria_id -> exam_criterias.id',
 'childTable','exam_versions','referenceColumn','exam_criteria_id','parentTable','exam_criterias',
 'schemaAvailable',true,'orphanRows',(SELECT count(*) FROM missing),
 'samples',COALESCE((SELECT json_agg(s) FROM
  (SELECT child_id AS "childId",missing_parent_id AS "missingParentId"
   FROM missing ORDER BY child_id LIMIT __SAMPLE_LIMIT__) s),'[]'::json));
\else
SELECT json_build_object('kind','relation','relationship','exam_versions.exam_criteria_id -> exam_criterias.id',
 'childTable','exam_versions','referenceColumn','exam_criteria_id','parentTable','exam_criterias',
 'schemaAvailable',false,'orphanRows',NULL,'samples','[]'::json);
\endif

SELECT (
 EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_sessions') AND attname='id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_sessions') AND attname='exam_version_id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_versions') AND attname='id' AND attnum>0 AND NOT attisdropped)
) AS link_4_available \gset
\if :link_4_available
WITH missing AS MATERIALIZED (
 SELECT c.id AS child_id,c.exam_version_id AS missing_parent_id
 FROM public.exam_sessions c
 LEFT JOIN public.exam_versions p ON p.id=c.exam_version_id
 WHERE c.exam_version_id IS NOT NULL AND p.id IS NULL
)
SELECT json_build_object('kind','relation','relationship','exam_sessions.exam_version_id -> exam_versions.id',
 'childTable','exam_sessions','referenceColumn','exam_version_id','parentTable','exam_versions',
 'schemaAvailable',true,'orphanRows',(SELECT count(*) FROM missing),
 'samples',COALESCE((SELECT json_agg(s) FROM
  (SELECT child_id AS "childId",missing_parent_id AS "missingParentId"
   FROM missing ORDER BY child_id LIMIT __SAMPLE_LIMIT__) s),'[]'::json));
\else
SELECT json_build_object('kind','relation','relationship','exam_sessions.exam_version_id -> exam_versions.id',
 'childTable','exam_sessions','referenceColumn','exam_version_id','parentTable','exam_versions',
 'schemaAvailable',false,'orphanRows',NULL,'samples','[]'::json);
\endif

SELECT (
 EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_session_answers') AND attname='id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_session_answers') AND attname='exam_session_id' AND attnum>0 AND NOT attisdropped)
 AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('public.exam_sessions') AND attname='id' AND attnum>0 AND NOT attisdropped)
) AS link_5_available \gset
\if :link_5_available
WITH missing AS MATERIALIZED (
 SELECT c.id AS child_id,c.exam_session_id AS missing_parent_id
 FROM public.exam_session_answers c
 LEFT JOIN public.exam_sessions p ON p.id=c.exam_session_id
 WHERE c.exam_session_id IS NOT NULL AND p.id IS NULL
)
SELECT json_build_object('kind','relation','relationship','exam_session_answers.exam_session_id -> exam_sessions.id',
 'childTable','exam_session_answers','referenceColumn','exam_session_id','parentTable','exam_sessions',
 'schemaAvailable',true,'orphanRows',(SELECT count(*) FROM missing),
 'samples',COALESCE((SELECT json_agg(s) FROM
  (SELECT child_id AS "childId",missing_parent_id AS "missingParentId"
   FROM missing ORDER BY child_id LIMIT __SAMPLE_LIMIT__) s),'[]'::json));
\else
SELECT json_build_object('kind','relation','relationship','exam_session_answers.exam_session_id -> exam_sessions.id',
 'childTable','exam_session_answers','referenceColumn','exam_session_id','parentTable','exam_sessions',
 'schemaAvailable',false,'orphanRows',NULL,'samples','[]'::json);
\endif

COMMIT;
'@
$sql=$sql.Replace('__SAMPLE_LIMIT__',[string]$SampleLimit)
$arguments=@('--context',$context,'exec','-i')
if($env:PGPASSWORD) {$arguments+=@('-e','PGPASSWORD')}
$arguments+=@($containerId,'psql','-X','-w','-q','-t','-A','-U',$DatabaseUser,'-d',$Database,
    '-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate','-v',('expected_database='+$Database),'-f','-')
$result=InvokeDocker $arguments $sql
$items=[Collections.Generic.List[object]]::new()
foreach($line in $result.Lines) {
    if($line.Trim().StartsWith('{')) {
        $items.Add((ConvertFrom-Json -InputObject $line))
    }
}
$scope=@($items.ToArray() | Where-Object {$_.kind -eq 'scope'})
$history=@($items.ToArray() | Where-Object {$_.kind -eq 'history'})
$relations=@($items.ToArray() | Where-Object {$_.kind -eq 'relation'})
$available=@($relations | Where-Object {$_.schemaAvailable -eq $true})
$completed=$result.Code -eq 0 -and $scope.Count -eq 1 -and
    $scope[0].database -ceq $Database -and $scope[0].readOnly -eq $true -and
    $relations.Count -eq 6 -and $available.Count -eq 6 -and $history.Count -eq 1
$stateMatch=[regex]::Match(($result.Lines -join "`n"),'(?:ERROR|FATAL):\s*([0-9A-Z]{5})')
$sqlState=if($stateMatch.Success){$stateMatch.Groups[1].Value}else{$null}
$total=0
foreach($relation in $available) {$total+=[long]$relation.orphanRows}
$report=[ordered]@{
    DiagnosticRevision=2
    DiagnosticCompleted=$completed
    Database=$Database
    DockerContext=$context
    ContainerId=$containerId
    ReadOnlyTransactionVerified=($scope.Count -eq 1 -and $scope[0].readOnly -eq $true)
    DatabaseModified=$false
    SchemaFilesModified=$false
    SchemaScope=$scope
    MigrationHistory=$history
    Relationships=$relations
    TotalOrphanReferences=$total
    SampleLimitPerRelationship=$SampleLimit
    PsqlExit=$result.Code
    SQLState=$sqlState
    FinishedAt=[DateTimeOffset]::UtcNow.ToString('o')
}
$reportDirectory=Join-Path $DtsRoot 'handoff'
[IO.Directory]::CreateDirectory($reportDirectory) | Out-Null
$reportPath=Join-Path $reportDirectory ('screen12-fk-diagnostic-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,6)+'.json')
$report['ReportPath']=$reportPath
[IO.File]::WriteAllText($reportPath,(ConvertTo-Json -InputObject $report -Depth 12),[Text.UTF8Encoding]::new($false))
if(-not $PassThru) {
 $relations | Select-Object relationship,schemaAvailable,orphanRows | Format-Table -AutoSize
 [pscustomobject]@{
    DiagnosticCompleted=$completed
    ReadOnlyTransactionVerified=$report.ReadOnlyTransactionVerified
    TotalOrphanReferences=$total
    MigrationHistoryPresent=($history.Count -eq 1 -and $history[0].present -eq $true)
    DatabaseModified=$false
    ReportPath=$reportPath
 } | Format-List
}
if(-not $completed) {throw "Read-only diagnostic incomplete (exit=$($result.Code); SQLState=$sqlState). Send $reportPath and this message."}
Write-Host 'Read-only diagnostic completed. Send the generated JSON report; it contains link counts and sample row IDs, without account credentials or answer snapshots.'
if($PassThru) {[pscustomobject]$report}
