#!/usr/bin/env bash
# verify-catalog.sh
# Usage: bash .agents/skills/json-render-transpiler/scripts/verify-catalog.sh [frontend-dir]
#
# Reads @json-render/shadcn type definitions to produce the authoritative
# component list. Run this before resolving any component keys in a transpilation.

FRONTEND_DIR="${1:-engineers/03-implementations/frontend}"
PKG="$FRONTEND_DIR/node_modules/@json-render/shadcn/dist"

if [ ! -d "$PKG" ]; then
  echo "❌ ERROR: @json-render/shadcn not found at $PKG"
  echo "   Run: cd $FRONTEND_DIR && npm install"
  exit 1
fi

echo "========================================================"
echo "  @json-render/shadcn — Available Components"
echo "========================================================"

echo ""
echo "── shadcnComponents (runtime implementations, index.d.ts) ──"
grep -E "^    [A-Z][a-zA-Z]+:" "$PKG/index.d.ts" | sed 's/:.*//' | tr -d ' ' | sort

echo ""
echo "── shadcnComponentDefinitions (Zod schemas, catalog.d.ts) ──"
grep -E "^    [A-Z][a-zA-Z]+:" "$PKG/catalog.d.ts" | sed 's/:.*//' | tr -d ' ' | sort

echo ""
echo "── Components in index but NOT in catalog (runtime-only) ──"
INDEX_KEYS=$(grep -E "^    [A-Z][a-zA-Z]+:" "$PKG/index.d.ts" | sed 's/:.*//' | tr -d ' ' | sort)
CATALOG_KEYS=$(grep -E "^    [A-Z][a-zA-Z]+:" "$PKG/catalog.d.ts" | sed 's/:.*//' | tr -d ' ' | sort)
comm -23 <(echo "$INDEX_KEYS") <(echo "$CATALOG_KEYS") || echo "(none)"

echo ""
echo "── Prop shape for a specific component (example: Input) ──"
echo "   Run: sed -n '/^    Input:/,/^    [A-Z]/p' $PKG/catalog.d.ts | head -40"

echo ""
echo "========================================================"
echo "  ⚠️  Components NOT available in @json-render/shadcn:"
echo "    Breadcrumb, AlertDialog, DatePicker, MultiSelect,"
echo "    Sonner/Toast, CommandPalette"
echo "========================================================"
