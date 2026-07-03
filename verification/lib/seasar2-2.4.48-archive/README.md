# seasar2 2.4.48 ビルド成果物(アーカイブ)

`github.com/seasarorg/seasar2` タグ `Seasar2.4.48` から本環境(JDK8)でソースビルドした jar。

- `s2-framework-2.4.48.jar`
- `s2-extension-2.4.48.jar`
- `s2-tiger-2.4.48.jar`

maven.seasar.org 停止によりバイナリが失われているため、ソースビルド成果物として保全する目的で
コミットしている。

**これらは実行時ランタイムには使用していない。** 本プロジェクトの S2Dao(`s2-dao 1.0.52`)は
`s2-extension 2.3.23` の API に依存しており、2.4.48 の s2-extension とは実行時非互換
(`ValueType` インタフェース等が 2.3→2.4 で変更)。実行時ランタイムは `../` 直下の
`s2-framework-2.3.23.jar` / `s2-extension-2.3.23.jar` + `s2-dao-1.0.52.jar` を使う。

ビルド方法・除外パッケージ・JDBC3 スタブの詳細は `docs/research/RUNTIME_BUILD.md` を参照。
再ビルドは `verification/env/build-seasar2.sh`。
