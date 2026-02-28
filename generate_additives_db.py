import json, re, pathlib, argparse, sys

# Use project-local assets directory instead of external Documents
ASSETS_DIR = pathlib.Path(r"C:/Users/tomin/AndroidStudioProjects/MyApplication/app/src/main/assets/additives")
SOURCE_FILES = [
    ASSETS_DIR / "aditivi100-199.txt",
    ASSETS_DIR / "aditivi200-299.txt",
    ASSETS_DIR / "aditivi300-399.txt",
    ASSETS_DIR / "aditivi400-499.txt",
    ASSETS_DIR / "aditivi500-599.txt",
    ASSETS_DIR / "aditivi600-699.txt",
    ASSETS_DIR / "aditivi900-999.txt",
    ASSETS_DIR / "aditivi1000-1599.txt",
    # Newly added supplementary file with missing additives
    ASSETS_DIR / "dodatek.txt",
]
MASTER_JSON = pathlib.Path(r"C:/Users/tomin/AndroidStudioProjects/MyApplication/app/src/main/assets/e_additives_database.json")
# Emit additional optimized artifacts without changing existing behavior/paths
MASTER_JSON_MIN = pathlib.Path(r"C:/Users/tomin/AndroidStudioProjects/MyApplication/app/src/main/assets/e_additives_database.min.json")
MASTER_INDEX_JSON = pathlib.Path(r"C:/Users/tomin/AndroidStudioProjects/MyApplication/app/src/main/assets/e_additives_index.json")

# Parse ALL E-codes from all source files
CODE_LINE_RE = re.compile(r"^(E\d+[a-zA-Z]?)(\s*-\s*.*)?$")  # All E-codes
SECTION_RE = re.compile(r"^(Name|Description|Function|Origin|Health risks|Usage in foods|Acceptable daily intake \(ADI\)|Other details):\s*(.*)$", re.IGNORECASE)
STRIP_CODE_IN_NAME_RE = re.compile(r"^E\d+[a-zA-Z]?\s*-\s*", re.IGNORECASE)

EMPTY_MARKERS = {
    "not specified", "no data", "insufficient relevant content", "value specified (not provided in excerpt)",
    "specified, but without a specific value in the extract", "not limited or determined", "banned (no detail)",
}

RISK_KEYWORDS = {
    'HIGH': ['carcin', 'tumor', 'chromosome damage', 'banned'],
    'MODERATE': ['hyperactivity', 'allerg', 'nausea', 'migraine', 'hives', 'abdominal pain'],
}

# Debug helper: record codes seen per file
_seen_codes = {}

def classify(health: str):
    h = health.lower()
    if any(k in h for k in RISK_KEYWORDS['HIGH']):
        return 'HIGH'
    if any(k in h for k in RISK_KEYWORDS['MODERATE']):
        return 'MODERATE'
    if not h:
        return 'UNKNOWN'
    return 'LOW'

def parse_sources() -> list:
    additives = []
    current = None
    last_section_key = None
    for path in SOURCE_FILES:
        file_codes = []
        if not path.exists():
            print(f"WARNING missing file: {path}")
            continue
        lines = path.read_text(encoding='utf-8', errors='ignore').splitlines()
        for raw_line in lines:
            line = raw_line.strip()
            if not line:
                continue

            m_code = CODE_LINE_RE.match(line)
            if m_code:
                # Flush previous
                if current:
                    additives.append(current)
                code = m_code.group(1)
                file_codes.append(code)
                current = {
                    'code': code,
                    'name': '',
                    'description': '',
                    'function': '',
                    'origin': '',
                    'healthRisks': '',
                    'usage': '',
                    'adi': '',
                    'otherDetails': ''
                }
                last_section_key = None
                continue

            m_sec = SECTION_RE.match(line)
            if m_sec and current:
                label = m_sec.group(1).lower()
                value = m_sec.group(2).strip()
                key_map = {
                    'name': 'name',
                    'description': 'description',
                    'function': 'function',
                    'origin': 'origin',
                    'health risks': 'healthRisks',
                    'usage in foods': 'usage',
                    'acceptable daily intake (adi)': 'adi',
                    'other details': 'otherDetails'
                }
                k = key_map[label]
                last_section_key = k
                low_val = value.lower().rstrip('.')
                if any(low_val.startswith(x) for x in EMPTY_MARKERS):
                    # keep marker inside otherDetails for traceability
                    if k != 'otherDetails' and low_val.startswith('insufficient relevant content'):
                        current['otherDetails'] = (current['otherDetails'] + ' ' + value).strip()
                    value = ''
                if k == 'name':
                    value = STRIP_CODE_IN_NAME_RE.sub('', value).strip()
                current[k] = value
                continue

            # Continuation lines for multi-paragraph description etc. - but only if we have started parsing an additive
            if current and last_section_key:
                if not CODE_LINE_RE.match(line) and not SECTION_RE.match(line):
                    # Append to last section with space
                    current[last_section_key] = (current[last_section_key] + ' ' + line).strip()

        # Log progress for this file
        file_count_after = len(additives)
        if current:
            additives.append(current)
            file_count_after += 1
            current = None  # Reset for next file
        _seen_codes[path.name] = file_codes
        print(f"Parsed {path.name}: {len(file_codes)} codes -> {file_codes[:10]}{'...' if len(file_codes)>10 else ''}")

    # Final flush if not done
    if current:
        additives.append(current)

    # Deduplicate by code preferring richer record (unlikely duplicates here)
    by_code = {}
    for a in additives:
        code = a['code']
        score = sum(1 for k,v in a.items() if k!='code' and v)
        prev = by_code.get(code)
        if prev is None or score > sum(1 for k,v in prev.items() if k!='code' and v):
            by_code[code] = a

    final = list(by_code.values())
    final.sort(key=lambda x: (int(re.findall(r"\d+", x['code'])[0]), x['code']))

    missing_examples = [c for c in ["E101","E102","E103","E107"] if c not in by_code]
    if missing_examples:
        print(f"WARNING expected codes missing after parse: {missing_examples}")

    for a in final:
        a['riskLevel'] = classify(a['healthRisks'])
        # Fallback if name empty -> use code
        if not a['name']:
            a['name'] = a['code']

    # No need to add placeholders - only include E-codes found in source files
    final.sort(key=lambda x: (int(re.findall(r"\d+", x['code'])[0]), x['code']))

    return final

def write_json(data: list, path: pathlib.Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

# New: write minified JSON to reduce APK size / load time (optional artifact)
def write_min_json(data: list, path: pathlib.Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Minified with stable ordering
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')

# New: small index for O(1) lookup by code without scanning the whole array (optional artifact)
def build_index(data: list) -> dict:
    # Map E-code -> array index; also include a compact list of codes to allow quick existence checks
    by_code = {a['code']: i for i, a in enumerate(data)}
    return {
        'count': len(data),
        'codes': list(by_code.keys()),
        'byCode': by_code,
    }

def export_to_text(data: list, path: pathlib.Path):
    lines=[]
    for a in data:
        lines.append(f"{a['code']} - {a['name']}")
        lines.append("")
        def sec(label,key):
            val=a.get(key,'').strip()
            if val:
                lines.append(f"{label}: {val}")
            else:
                lines.append(f"{label}: Not specified.")
        sec('Name','name')
        sec('Description','description')
        sec('Function','function')
        sec('Origin','origin')
        sec('Health risks','healthRisks')
        sec('Usage in foods','usage')
        sec('Acceptable daily intake (ADI)','adi')
        sec('Other details','otherDetails')
        lines.append("")
    path.write_text("\n".join(lines), encoding='utf-8')

def main():
    ap=argparse.ArgumentParser(description='Manage E-additives dataset (all E-codes from source files).')
    ap.add_argument('--to-json', action='store_true', help='Parse source TXT files and write master JSON.')
    ap.add_argument('--from-json', metavar='OUT_TXT', help='Generate consolidated text file from master JSON.')
    ap.add_argument('--json', default=str(MASTER_JSON), help='Path to master JSON file.')
    # Optional flags to skip extra artifacts if desired
    ap.add_argument('--no-minify', action='store_true', help='Do not write the minified JSON artifact.')
    ap.add_argument('--no-index', action='store_true', help='Do not write the index JSON artifact.')
    args=ap.parse_args()
    json_path=pathlib.Path(args.json)
    if args.to_json:
        data=parse_sources()
        write_json(data,json_path)
        if not args.no_minify:
            write_min_json(data, MASTER_JSON_MIN)
        if not args.no_index:
            write_json(build_index(data), MASTER_INDEX_JSON)
        print(f"Wrote JSON {json_path} with {len(data)} records")
        if not args.no_minify:
            print(f"Wrote minified JSON -> {MASTER_JSON_MIN}")
        if not args.no_index:
            print(f"Wrote index JSON -> {MASTER_INDEX_JSON}")
    elif args.from_json:
        if not json_path.exists():
            print(f"JSON not found: {json_path}", file=sys.stderr); sys.exit(1)
        data=json.loads(json_path.read_text(encoding='utf-8'))
        out_txt=pathlib.Path(args.from_json)
        export_to_text(data,out_txt)
        print(f"Exported text dataset -> {out_txt}")
    else:
        # Default behavior (like before)
        data=parse_sources()
        write_json(data, MASTER_JSON)
        # Also emit optimized artifacts by default for convenience
        write_min_json(data, MASTER_JSON_MIN)
        write_json(build_index(data), MASTER_INDEX_JSON)
        rich=[d for d in data if d.get('description')][:5]
        print(f"Generated {len(data)} additives -> {MASTER_JSON}")
        print(f"Also wrote minified -> {MASTER_JSON_MIN} and index -> {MASTER_INDEX_JSON}")
        print("Preview:")
        for r in rich:
            print(json.dumps(r, ensure_ascii=False)[:200])

if __name__=='__main__':
    main()
