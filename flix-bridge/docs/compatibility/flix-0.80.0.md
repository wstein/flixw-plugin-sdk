# Flix 0.80.0 compatibility investigation

## Inputs

- Release: [`v0.80.0`](https://github.com/flix/flix/releases/tag/v0.80.0) (published 2026-11-15)
- Release commit: [`0c641c73c4635dee992d01c4364f224ea56d9a46`](https://github.com/flix/flix/commit/0c641c73c4635dee992d01c4364f224ea56d9a46)
- Compiler artifact SHA-256: `0e9dec1e93071ada5eaba2e187499f0880b574e7e4ef62937d6f31c661c1fda0` (the digest GitHub lists for the `flix.jar` release asset)
- Compared with: [`v0.79.0...v0.80.0`](https://github.com/flix/flix/compare/v0.79.0...v0.80.0) (168 commits)
- Analyzer implementation under test: `Flix0800Adapter`, a copy of `Flix0761Adapter` with the changes below
- Environment: Temurin 21.0.13+9, Linux aarch64, 2026-11-18

## Upstream changes reviewed

- The `language/fmt` formatters were replaced. `FormatType` and eight sibling `Format*` files are
  deleted; `Pretty`, `RichText` and `Emitter` are added; `DisplayType` and `FormatOptions` are
  rewritten. `Pretty.tpe(t: Type)(implicit o: FormatOptions): RichText` with `.plain: String`
  renders a type; it takes no `Flix` argument. The release notes (third-party text) say that
  types and effects are printed differently in error messages.
- `Lexer.lex(src)` returns `(SourceTokens, List[LexerError])` instead of
  `(Array[Token], List[LexerError])`; `SourceTokens(tokens, semanticTokens)` is a case class. The
  erased descriptor stays `Lscala/Tuple2;`. The JVM `Signature` attribute of `Lexer$.lex` names
  `[Lca/uwaterloo/flix/language/ast/Token;` in 0.79.0 and `Lca/uwaterloo/flix/language/ast/SourceTokens;`
  in 0.80.0 (`javap -v`).
- `TypedAst` (+69/-52 lines), `TokenKind` (+105/-173) and the type inference changed (sub-effecting
  for lambdas and local defs, faster inference; release notes). Not reviewed member by member:
  the gate and the fixture are the evidence for them.

## Adapter impact

`Flix0761Adapter` references 222 members; the ABI gate reports 6 missing on 0.80.0, all in
`FormatType` (the class, `MODULE$`, `formatType` and three `formatType$default$N`). No adapter
of an older family links. A new adapter is required.

`Flix0800Adapter` differs from `Flix0761Adapter` by 9 added and 7 removed lines (`diff` of the two
files): the `Pretty` import, the class name and its documentation line, three
`FormatType.formatType` calls replaced by `Pretty.tpe(..).plain`, and the unpacking of
`SourceTokens` in `lexSource` (a comment line and `val tokens = sourceTokens.tokens`). It has 226
references.

Error behavior differs. In 0.79.0 `FormatType.formatType` caught every `Throwable` and returned
`"ERR_UNABLE_TO_FORMAT_TYPE"` ([`FormatType.scala`](https://github.com/flix/flix/blob/v0.79.0/main/src/ca/uwaterloo/flix/language/fmt/FormatType.scala#L65-L66)).
`Pretty.tpe` ([`Pretty.scala#L36`](https://github.com/flix/flix/blob/v0.80.0/main/src/ca/uwaterloo/flix/language/fmt/Pretty.scala#L36-L37))
catches nothing, so an `InternalCompilerException` from `DisplayType` would reach `measure`.
`DisplayType` still throws `"unexpected Type.Apply"` for an application whose head is not a type
constructor ([`DisplayType.scala#L735`](https://github.com/flix/flix/blob/v0.80.0/main/src/ca/uwaterloo/flix/language/fmt/DisplayType.scala#L735));
over-applied constructors go to `overApplied` and `Error` types to `DisplayType.Error` in the same
match. Not verified: whether types of the typed AST can reach that throw; the fixture does not.
Not visible to the ABI gate: the `Lexer.lex` result type. `Flix0761AdapterTest` on 0.80.0 ends with
a `ClassCastException` (`SourceTokens` to `Token[]`) at the `Lexer.lex` call; the source does not
compile against 0.80.0 either. The gate cannot see a changed type argument inside `Tuple2`, because
the adapter's call site records only the erased descriptor.

Semantic risks that the fixture does not cover: the widths of FlixDoc parameter and type
characters (`Pretty.tpe(t).plain` against `FormatType.formatType(t)`), effect types in the
`TypedAst` after the sub-effecting change, and declaration positions.

## Verification

| Check | Command or method | Result |
| --- | --- | --- |
| Artifact digest | `sha256sum` of the jar; `gh api repos/flix/flix/releases/tags/v0.80.0` | equal to the digest above and to the GitHub asset digest |
| Adapter source build | `./mill --no-server flix-bridge.adapter.flix0800.compile` against `flix-0.80.0.jar` (`-Xfatal-warnings`) | passed |
| Bytecode-derived ABI gate | `AdapterAbi.missing` per family on 0.80.0 and on 0.79.0 | 0.80.0: `Flix0800Adapter` 0 of 226 missing, `Flix0761Adapter` 6 of 222, `Adapters.select()` = `flix0800`. 0.79.0: `Flix0800Adapter` 8 of 226 missing, `Flix0761Adapter` 0, `select()` = `flix0761` |
| Semantic fixture | `Flix0800AdapterTest` on `semantic` with 0.80.0 | `ok`; the conformance run gives `defs=6 measurement=0d5322308d140a9b`, the digest of 0.76.1 through 0.79.0 |
| Negative controls | `Flix0761AdapterTest` on 0.80.0; `Flix0800AdapterTest` on 0.79.0 | both fail |
| Packaged integration | `scripts/check-package.sh`: example plugin on 0.80.0 | selects `flix0800`, 6 definitions, no warning. The metrics `test-compiler-compatibility.sh` checkpoint: not run |
| Corpus calibration | not run | not run |
| Performance contract | not run | not run |
| Full test suite | `./mill --no-server flix-bridge.test.check` | see the pull request |

## Decision and compatibility impact

- Supported range and adapter: Flix 0.80.0 uses `Flix0800Adapter`; 0.76.1 through 0.79.0 stay on
  `Flix0761Adapter`.
- Required implementation changes: the three type renderings and the lexer unpacking above.
- Report schema, wire format, SDK and capability JSON, CLI: unchanged by this record.
- Cache: the adapter family is a cache field (`adapterFamily=flix0800`), so entries of 0.80.0 and
  0.79.0 do not mix.
- Pin: the new family is first in `versions.json`, and its only release is its `testVersion`.
  `sdk-versions.sh pin` in flixw-metrics reads `families[0].testVersion` and names 0.80.0 after
  this change.
- Follow-up work: flixw-metrics pin change with the packaged integration checkpoint, span
  comparison and corpus run for 0.80.0; a comparison of FlixDoc widths against 0.79.0; a gate
  check of generic signatures (see the pull request).
