<<<<<<< HEAD
tea_version="v1.0.0-beta.1"
=======
<<<<<<< HEAD
tea_version="v1.0.0-beta.2"
=======
tea_version="v1.0.0-beta.1"
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)

cd /tmp/ || exit
git clone https://github.com/ljp-projects/TeaScript.git
cd TeaScript || exit
mv src/TeaScript.jar /usr/local/bin/
touch tea
echo "java -jar /usr/local/bin/TeaScript.jar \$*" > tea
touch tea-version
echo "$tea_version" > tea-version
chmod +x tea
mv tea /usr/local/bin
mv tea-version /usr/local/bin
cd ..
rm -rf TeaScript