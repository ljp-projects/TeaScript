tea_version=$(curl  "https://api.github.com/repos/ljp-projects/teascript/tags" | jq -r '.[0].name')

cd /usr/local/bin/ || exit
mv "$1" ./

touch tea
echo "java -jar /usr/local/bin/TeaScript.jar \$*" > tea

touch tea-version
echo "$tea_version" > tea-version

chmod +x tea