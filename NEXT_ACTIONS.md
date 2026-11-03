# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 9/11 (81.8%)
- **Function parity:** 197/270 matched (target 319) — 73.0%
- **Class/type parity:** 41/52 matched (target 59) — 78.8%
- **Combined symbol parity:** 238/322 matched (target 378) — 73.9%
- **Average inline-code cosine:** 0.11 (function body across 9 matched files)
- **Average documentation cosine:** 0.52 (doc text across 9 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 9 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 0 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. string

- **Target:** `regexlite.Regex`
- **Similarity:** 0.13
- **Dependents:** 1
- **Priority Score:** 1351508.8
- **Functions:** 64/94 matched (target 103)
- **Missing functions:** `clone`, `fmt`, `fmt`, `from_str`, `try_from`, `try_from`, `start`, `end`, `range`, `fmt`, `from`, `fmt`, `fmt`, `fmt`, `fmt`, `index`, `index`, `size_hint`, `size_hint`, `size_hint`, `no_expansion`, `replace_append`, `no_expansion`, `replace_append`, `replace_append`, `no_expansion`, `replace_append`, `no_expansion`, `no_expansion`, `new`
- **Types:** 17/21 matched (target 20)
- **Missing types:** `Err`, `Error`, `Output`, `Item`

### 2. pikevm

- **Target:** `pikevm.PikeVM`
- **Similarity:** 0.16
- **Dependents:** 1
- **Priority Score:** 1134308.4
- **Functions:** 22/33 matched
- **Missing functions:** `new`, `new`, `new`, `new`, `reset`, `for_state`, `new`, `resize`, `iter`, `fmt`, `next`
- **Types:** 8/10 matched
- **Missing types:** `Item`, `SparseSetIter`

### 3. nfa

- **Target:** `nfa.Nfa`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 134608.7
- **Functions:** 27/37 matched (target 29)
- **Missing functions:** `default`, `new`, `pattern`, `start`, `is_start_anchored`, `static_explicit_captures_len`, `fmt`, `next`, `fmt`, `new`
- **Types:** 6/9 matched (target 13)
- **Missing types:** `CaptureNames`, `Item`, `CaptureNameMap`

### 4. hir.parse

- **Target:** `hir.Parser`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 107707.5
- **Functions:** 66/76 matched (target 74)
- **Missing functions:** `new`, `pattern`, `flags`, `p`, `perr`, `class`, `singles`, `posix`, `cap`, `named_cap`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Tests:** 21/28 matched

### 5. pool

- **Target:** `regexlite.Pool`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 61409.7
- **Functions:** 3/8 matched (target 7)
- **Missing functions:** `fmt`, `drop`, `deref`, `deref_mut`, `fmt`
- **Types:** 5/6 matched
- **Missing types:** `Target`

### 6. interpolate

- **Target:** `regexlite.Interpolate`
- **Similarity:** 0.06
- **Dependents:** 0
- **Priority Score:** 30909.4
- **Functions:** 4/7 matched (target 53)
- **Missing functions:** `from`, `from`, `interpolate_string`
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 7. int

- **Target:** `regexlite.Int`
- **Similarity:** 0.02
- **Dependents:** 0
- **Priority Score:** 20609.8
- **Functions:** 3/4 matched (target 7)
- **Missing functions:** `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `U32`

### 8. error

- **Target:** `regexlite.Error`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20310.0
- **Functions:** 0/2 matched (target 3)
- **Missing functions:** `new`, `fmt`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 9. utf8

- **Target:** `regexlite.Utf8`
- **Similarity:** 0.20
- **Dependents:** 0
- **Priority Score:** 10908.0
- **Functions:** 8/9 matched (target 10)
- **Missing functions:** `d`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 3/4 matched

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `hir.mod` | `hir.Mod` | 0 | `hir/mod.rs` | `hir/Mod.kt` |
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |

