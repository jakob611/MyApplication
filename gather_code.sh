#!/bin/bash
# =============================================================================
# gather_code.sh — Glow Upp: Deep-Context AI Architectural Audit Aggregator
# =============================================================================
# NAVODILO: Pred prvim zagonom daj skripta izvajalne pravice:
#   chmod +x gather_code.sh
#   ./gather_code.sh
#
# Rezultat: glowupp_source_audit.txt v korenu projekta (~celotna koda baza)
# =============================================================================

# ── Konfiguracija ─────────────────────────────────────────────────────────────
OUTPUT_FILE="glowupp_source_audit.txt"

# Iskalni direktoriji (relativno od korena projekta)
SEARCH_DIRS=(
    "app/src/main/java"
    "domain"
    "data"
    "ui"
)

# Direktoriji ki jih POPOLNOMA ignoriramo
EXCLUDE_DIRS=(
    "build"
    ".gradle"
    ".git"
    "app/build"
    "androidTest"
    "test"
)

# ── Inicializacija ─────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# Počisti ali ustvari izhodno datoteko
> "$OUTPUT_FILE"

# ── Zgradi find -prune argumente za izključene mape ────────────────────────────
PRUNE_ARGS=()
for excl in "${EXCLUDE_DIRS[@]}"; do
    PRUNE_ARGS+=(-path "*/${excl}/*" -prune -o)
    PRUNE_ARGS+=(-path "*/${excl}" -prune -o)
done

# ── Statistika ─────────────────────────────────────────────────────────────────
FILE_COUNT=0
TOTAL_LINES=0

echo "=== Glow Upp Source Audit — $(date '+%Y-%m-%d %H:%M:%S') ===" >> "$OUTPUT_FILE"
echo "=== Generirano z: gather_code.sh ===" >> "$OUTPUT_FILE"
echo "=== Iskani direktoriji: ${SEARCH_DIRS[*]} ===" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# ── Zbiranje datotek ───────────────────────────────────────────────────────────
echo "[gather_code.sh] Začetek zbiranja .kt datotek..."
echo "[gather_code.sh] Iskalniki direktoriji: ${SEARCH_DIRS[*]}"
echo "[gather_code.sh] Ignorirani direktoriji: ${EXCLUDE_DIRS[*]}"
echo ""

for SEARCH_DIR in "${SEARCH_DIRS[@]}"; do

    # Preveri ali direktorij obstaja
    if [ ! -d "$SEARCH_DIR" ]; then
        echo "[PRESKOK] Direktorij ne obstaja: $SEARCH_DIR"
        continue
    fi

    echo "[SKENIRANJE] $SEARCH_DIR ..."

    # Poiščemo vse .kt datoteke ob upoštevanju izključitev
    while IFS= read -r -d '' FILE; do

        # Relativna pot (od korena projekta)
        REL_PATH="${FILE#./}"

        # Pridobi število vrstic v datoteki
        FILE_LINES=$(wc -l < "$FILE" 2>/dev/null || echo 0)

        # ── Zapiši header ──────────────────────────────────────────────────────
        {
            echo "// =========================================="
            echo "// FILE PATH: ${REL_PATH}"
            echo "// =========================================="
        } >> "$OUTPUT_FILE"

        # ── Zapiši vsebino datoteke ────────────────────────────────────────────
        cat "$FILE" >> "$OUTPUT_FILE"

        # ── Dve prazni vrstici med datotekami ─────────────────────────────────
        printf "\n\n" >> "$OUTPUT_FILE"

        # Posodobi statistiko
        FILE_COUNT=$((FILE_COUNT + 1))
        TOTAL_LINES=$((TOTAL_LINES + FILE_LINES))

        echo "  [+] $REL_PATH ($FILE_LINES vrstic)"

    done < <(find "$SEARCH_DIR" \
        "${PRUNE_ARGS[@]}" \
        -name "*.kt" -print0 \
        2>/dev/null | sort -z)

done

# ── Zapiši nogo z statistiko ───────────────────────────────────────────────────
{
    echo ""
    echo "// =========================================="
    echo "// AUDIT SUMMARY"
    echo "// =========================================="
    echo "// Skupno datotek: ${FILE_COUNT}"
    echo "// Skupno vrstic:  ${TOTAL_LINES}"
    echo "// Generirano:     $(date '+%Y-%m-%d %H:%M:%S')"
    echo "// =========================================="
} >> "$OUTPUT_FILE"

# ── Končno poročilo v terminal ─────────────────────────────────────────────────
AUDIT_SIZE=$(du -sh "$OUTPUT_FILE" 2>/dev/null | cut -f1)

echo ""
echo "=============================================="
echo " ZBIRANJE ZAKLJUČENO ✅"
echo "=============================================="
echo " Izhod:         $OUTPUT_FILE"
echo " Velikost:      ${AUDIT_SIZE}"
echo " Datoteke:      ${FILE_COUNT} .kt datotek"
echo " Skupaj vrstic: ${TOTAL_LINES}"
echo "=============================================="
echo ""
echo " Naslednji korak — AI avdit:"
echo "   1. Odpri $OUTPUT_FILE"
echo "   2. Kopiraj vsebino v AI (Claude/GPT-4) kontekst"
echo "   3. Zahtevaj 'Full architectural audit' ali 'Find all violations of Clean Architecture'"
echo "=============================================="

