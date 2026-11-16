# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 9/11 (81.8%)
- **Function parity:** 173/270 matched (target 221) — 64.1%
- **Class/type parity:** 41/52 matched (target 55) — 78.8%
- **Combined symbol parity:** 214/322 matched (target 276) — 66.5%
- **Average inline-code cosine:** 0.09 (function body across 9 matched files)
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

- **Target:** `Regex`
- **Similarity:** 0.13
- **Dependents:** 1
- **Priority Score:** 1351508.8
- **Functions:** 64/94 matched (target 84)
- **Missing functions:** `clone`, `fmt`, `fmt`, `from_str`, `try_from`, `try_from`, `start`, `end`, `range`, `fmt`, `from`, `fmt`, `fmt`, `fmt`, `fmt`, `index`, `index`, `size_hint`, `size_hint`, `size_hint`, `no_expansion`, `replace_append`, `no_expansion`, `replace_append`, `replace_append`, `no_expansion`, `replace_append`, `no_expansion`, `no_expansion`, `new`
- **Types:** 17/21 matched (target 19)
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

### 3. hir.parse

- **Target:** `hir.Parser`
- **Similarity:** 0.15
- **Dependents:** 0
- **Priority Score:** 317708.5
- **Functions:** 45/76 matched (target 45)
- **Missing functions:** `new`, `pattern`, `flags`, `p`, `perr`, `class`, `singles`, `posix`, `cap`, `named_cap`, `ok_literal`, `ok_meta_escapes`, `ok_special_escapes`, `ok_hex`, `ok_perl`, `ok_flags_and_primitives`, `ok_alternate`, `ok_flag_group`, `ok_flag_directive`, `ok_uncounted_repetition`, `ok_counted_repetition`, `ok_group_unnamed`, `ok_group_named`, `ok_class`, `ok_verbatim`, `ok_comments`, `err_standard`, `err_verbatim`, `regression_454_nest_too_big`, `regression_455_trailing_dash_ignore_whitespace`, `regression_capture_indices`
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Tests:** 0/28 matched

### 4. nfa

- **Target:** `nfa.Nfa`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 134608.7
- **Functions:** 27/37 matched (target 29)
- **Missing functions:** `default`, `new`, `pattern`, `start`, `is_start_anchored`, `static_explicit_captures_len`, `fmt`, `next`, `fmt`, `new`
- **Types:** 6/9 matched (target 13)
- **Missing types:** `CaptureNames`, `Item`, `CaptureNameMap`

### 5. pool

- **Target:** `Pool`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 61409.7
- **Functions:** 3/8 matched (target 7)
- **Missing functions:** `fmt`, `drop`, `deref`, `deref_mut`, `fmt`
- **Types:** 5/6 matched
- **Missing types:** `Target`

### 6. utf8

- **Target:** `Utf8`
- **Similarity:** 0.11
- **Dependents:** 0
- **Priority Score:** 40908.9
- **Functions:** 5/9 matched (target 5)
- **Missing functions:** `decode_valid`, `d`, `decode_invalid`, `decode_lossily`
- **Types:** 0/0 matched
- **Missing types:** _none_
- **Tests:** 0/4 matched

### 7. interpolate

- **Target:** `Interpolate`
- **Similarity:** 0.06
- **Dependents:** 0
- **Priority Score:** 30909.4
- **Functions:** 4/7 matched (target 8)
- **Missing functions:** `from`, `from`, `interpolate_string`
- **Types:** 2/2 matched (target 4)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 8. int

- **Target:** `Int`
- **Similarity:** 0.02
- **Dependents:** 0
- **Priority Score:** 20609.8
- **Functions:** 3/4 matched (target 7)
- **Missing functions:** `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `U32`

### 9. error

- **Target:** `Error`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20310.0
- **Functions:** 0/2 matched (target 3)
- **Missing functions:** `new`, `fmt`
- **Types:** 1/1 matched
- **Missing types:** _none_

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

