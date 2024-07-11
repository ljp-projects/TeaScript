tea_version=$(curl -sS "https://api.github.com/repos/ljp-projects/teascript/tags" | jq -r '.[0].name')

message="TeaScript $tea_version has been installed successfully!"
length=${#message}
border=$(yes "-" | tr -d '\n' | head -c "$length")

FROM="NONE"
TAG="NONE"
BRANCH="NONE"
LEGACY=0
COMMIT="NONE"
KEEP=0

while [[ $# -gt 0 ]]; do
  case $1 in
    -t|--tag)
      TAG="$2"
      shift # past argument
      shift # past value
      ;;
    -b|--branch)
      BRANCH="$2"
      shift # past argument
      shift # past value
      ;;
    -c|--commit)
      COMMIT="$2"
      shift
      shift
      ;;
    -l|--legacy)
      LEGACY=0
      shift
      ;;
    -k|--keep)
      KEEP=1
      shift
      ;;
    -f|--from)
      FROM=$2
      shift
      shift
      ;;
    -*|--*)
      echo "Unknown option $1"
      exit 1
      ;;
    *)
      POSITIONAL_ARGS+=("$1") # save positional arg
      shift # past argument
      ;;
  esac
done

set -- "${POSITIONAL_ARGS[@]}" # restore positional parameters

cd /usr/local/bin/ || exit

# Authentication is required for this so that we can place files in /usr/local/bin
echo "You may be asked to authenticate. This is to maintain compatibility with Linux when completing the installation."

if [[ $TAG != "NONE" ]]; then
  curl -sSOL "https://github.com/ljp-projects/TeaScript/releases/download/${TAG}/TeaScript.jar"

  message="TeaScript ${TAG} has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")
elif [[ $BRANCH != "NONE" ]]; then
  curl -sS "https://raw.githubusercontent.com/ljp-projects/TeaScript/${BRANCH}/src/installer.sh" | sudo bash

  message="TeaScript from branch ${BRANCH} has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")
elif [[ $COMMIT != "NONE" ]]; then
  if [[ $COMMIT == "latest" ]]; then
    curl -sSOL "https://raw.githubusercontent.com/ljp-projects/TeaScript/main/src/installer.sh" | sudo bash
  else
    url="https://raw.githubusercontent.com/ljp-projects/TeaScript/$COMMIT/src/installer.sh"
    echo "Installing from $url..."
    curl -sSL "$url" | sudo bash
  fi

  message="TeaScript from commit $COMMIT has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")
  tea_version="from commit $COMMIT"
elif [[ $FROM == "NONE" ]]; then
  curl -sSOL "https://github.com/ljp-projects/TeaScript/releases/download/$tea_version/TeaScript.jar"
elif [[ $KEEP -eq 1 ]]; then
  message="TeaScript from JAR at $FROM has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")

  cp "$FROM" ./
else
  message="TeaScript from JAR at $FROM has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")

  mv "$FROM" ./
fi

touch tea
echo "java -jar /usr/local/bin/TeaScript.jar \$*" > tea

touch tea-version
echo "$tea_version" > tea-version

chmod +x tea

echo "$border"
echo "$message"
echo "$border"