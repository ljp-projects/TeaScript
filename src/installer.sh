tea_version=$(curl  "https://api.github.com/repos/ljp-projects/teascript/tags" | jq -r '.[0].name')

cd /tmp/ || exit
git clone https://github.com/ljp-projects/TeaScript.git

cd TeaScript || exit

xz -d -T8 src/TeaScript.jar.xz
cp src/TeaScript.jar /usr/local/bin/TeaScript.jar

touch tea
echo "java -jar /usr/local/bin/TeaScript.jar \$*" > tea

touch tea-version
echo "$tea_version" > tea-version

chmod +x tea

mv tea /usr/local/bin
mv tea-version /usr/local/bin

cd ..
rm -rf TeaScript