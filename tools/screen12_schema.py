#!/usr/bin/env python3
"""Apply checked-in schema versions with an existing psql connection configuration."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',required=True,help='Existing service database; uses PGHOST/PGPORT/PGUSER/PGPASSFILE or existing auth')
    parser.add_argument('--mode',choices=['apply','verify'],default='verify')
    parser.add_argument('--psql',default='psql',help='psql executable')
    args=parser.parse_args()
    root=Path(__file__).resolve().parent.parent
    manifest=json.loads((root/'docs/db/schema-manifest.json').read_text(encoding='utf-8'))
    for item in manifest['Files']:
        path=(root/item['Path']).resolve()
        if root not in path.parents or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest().upper()!=item['SHA256']:
            raise RuntimeError('Missing or edited schema file: '+item['Path'])
    if not args.database or '\x00' in args.database or args.database.startswith('-'):
        raise RuntimeError('Invalid database name')
    # A positional database value may be interpreted by libpq as a connection string.
    if any(x in args.database for x in ['=','://','\n','\r']):
        raise RuntimeError('Supply a database name, not a connection URL; retain existing PG* configuration')
    if (root/'src/main/java').is_dir():
        for mapping in manifest['EntityMappings']:
            path=root/mapping['Source']
            source=path.read_text(encoding='utf-8')
            table=re.search(r'@Table\s*\([^)]*\bname\s*=\s*"([^"]+)"',source,re.S)
            if not table or table.group(1)!=mapping['Table']:
                raise RuntimeError('JPA table mapping differs from SQL manifest: '+mapping['Source'])
    command=[args.psql,'-X','-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate','--dbname',args.database]
    if args.mode=='apply':
        command+=['-v','expected_database='+args.database,'-f',str(root/'docs/db/apply-schema.psql')]
        result=subprocess.run(command,check=False)
    else:
        sql='BEGIN READ ONLY; SET LOCAL search_path=public,pg_catalog; SET LOCAL statement_timeout=\'30s\';\n'
        sql+=(root/'docs/db/verify-schema.sql').read_text(encoding='utf-8')+'\nCOMMIT;\n'
        result=subprocess.run(command+['-f','-'],input=sql,encoding='utf-8',check=False)
    if result.returncode!=0:
        print('Schema '+args.mode+' failed; existing migration transaction must roll back. Review the database error.',file=sys.stderr)
        return result.returncode
    print(json.dumps({'service':manifest['Service'],'database':args.database,'mode':args.mode,'schema_verified':True}))
    return 0

if __name__=='__main__':
    try:sys.exit(main())
    except (OSError,ValueError,RuntimeError,KeyError) as failure:
        print(str(failure),file=sys.stderr);sys.exit(2)
