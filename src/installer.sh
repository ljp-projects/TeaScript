tea_version="v1.0.0-beta.2"

cd /tmp/ || exit

# We need to clone the legacy repo
git clone https://github.com/ljp-projects/TeaScript.git -b v1beta2-legacy-fixes

cd TeaScript || exit

unzip src/TeaScript.jar.zip src/TeaScript.jar
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
