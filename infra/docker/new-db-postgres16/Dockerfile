# PostgreSQL 16.8 に ja_JP.utf8 ロケールを追加したイメージ
# (公式 postgres イメージ(Debian ベース)は既定で ja_JP ロケールを持たないため、
#  README 記載の公式手順どおり localedef で生成する)
FROM postgres:16.8

RUN localedef -i ja_JP -c -f UTF-8 -A /usr/share/locale/locale.alias ja_JP.utf8
ENV LANG=ja_JP.utf8
