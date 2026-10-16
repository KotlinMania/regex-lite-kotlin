# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 11/11 (100.0%)
- **Function parity:** 194/302 matched (target 328) — 64.2%
- **Class/type parity:** 37/61 matched (target 59) — 60.7%
- **Combined symbol parity:** 231/363 matched (target 387) — 63.6%
- **Average inline-code cosine:** 0.08 (function body across 10 matched files)
- **Average documentation cosine:** 0.47 (doc text across 10 matched files)
- **Cheat-zeroed Files:** 4
- **Critical Issues:** 11 files with <0.60 function similarity
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
- **Similarity:** 0.10
- **Dependents:** 1
- **Priority Score:** 1601509.0
- **Functions:** 49/94 matched (target 82)
- **Missing functions:** `clone`, `fmt`, `fmt`, `from_str`, `try_from`, `try_from`, `new`, `new`, `start`, `end`, `range`, `fmt`, `from`, `fmt`, `fmt`, `fmt`, `fmt`, `index`, `index`, `next`, `count`, `next`, `count`, `next`, `next`, `size_hint`, `next`, `size_hint`, `count`, `next`, `size_hint`, `count`, `by_ref`, `no_expansion`, `replace_append`, `no_expansion`, `replace_append`, `no_expansion`, `replace_append`, `replace_append`, `no_expansion`, `replace_append`, `no_expansion`, `no_expansion`, `new`
- **Types:** 7/21 matched (target 10)
- **Missing types:** `Err`, `Error`, `CapturesDebugMap`, `Key`, `Value`, `Output`, `Matches`, `Item`, `CaptureMatches`, `Split`, `SplitN`, `CaptureNames`, `SubCaptureMatches`, `ReplacerRef`
- **Lint issues:** 1

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
- **Similarity:** 0.21
- **Dependents:** 0
- **Priority Score:** 187707.9
- **Functions:** 58/76 matched (target 65)
- **Missing functions:** `new`, `pattern`, `flags`, `p`, `perr`, `class`, `singles`, `posix`, `cap`, `named_cap`, `ok_group_unnamed`, `ok_group_named`, `ok_verbatim`, `ok_comments`, `err_verbatim`, `regression_454_nest_too_big`, `regression_455_trailing_dash_ignore_whitespace`, `regression_capture_indices`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Tests:** 13/28 matched

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

- **Target:** `regexlite.Pool [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 101410.0
- **Functions:** 2/8 matched (target 6)
- **Missing functions:** `new`, `fmt`, `drop`, `deref`, `deref_mut`, `fmt`
- **Types:** 2/6 matched (target 3)
- **Missing types:** `CachePool`, `CachePoolGuard`, `CachePoolFn`, `Target`

### 6. hir.mod

- **Target:** `hir.Hir [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 84110.0
- **Functions:** 24/32 matched (target 41)
- **Missing functions:** `default`, `kind`, `is_start_anchored`, `is_match_empty`, `static_explicit_captures_len`, `class`, `new`, `drop`
- **Types:** 9/9 matched (target 13)
- **Missing types:** _none_

### 7. interpolate

- **Target:** `regexlite.Interpolate`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 50909.6
- **Functions:** 2/7 matched (target 52)
- **Missing functions:** `string`, `from`, `from`, `is_valid_cap_letter`, `interpolate_string`
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 8. utf8

- **Target:** `regexlite.Utf8`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 20908.6
- **Functions:** 7/9 matched
- **Missing functions:** `mkwordset`, `d`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 3/4 matched

### 9. int

- **Target:** `regexlite.Int [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20610.0
- **Functions:** 3/4 matched (target 7)
- **Missing functions:** `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `U32`

### 10. error

- **Target:** `regexlite.Error`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20310.0
- **Functions:** 0/2 matched (target 3)
- **Missing functions:** `new`, `fmt`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 11. lib

- **Target:** `regexlite.Lib [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 1)
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

