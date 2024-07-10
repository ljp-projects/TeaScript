tea_version=$(curl -sS "https://api.github.com/repos/ljp-projects/teascript/tags" | jq -r '.[0].name')

POSITIONAL_ARGS=()
TAG="NONE"
LEGACY_BRANCH="NONE"

while [[ $# -gt 0 ]]; do
  case $1 in
    --tag)
      TAG="$2"
      shift # past argument
      shift # past value
      ;;
    -L|--legacy-branch)
      LEGACY_BRANCH="$2"
      shift # past argument
      shift # past value
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

if [[ $TAG != "NONE" ]]; then
  curl -sSOL "https://github.com/ljp-projects/TeaScript/releases/download/${TAG}/TeaScript.jar"

  message="TeaScript ${TAG} has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")

  echo "$border"
  echo "$message"
  echo "$border"

  exit
elif [[ $LEGACY_BRANCH != "NONE" ]]; then
  curl -sS "https://raw.githubusercontent.com/ljp-projects/TeaScript/${LEGACY_BRANCH}/src/installer.sh" | sh

  message="TeaScript from branch ${LEGACY_BRANCH} has been installed successfully!"
  length=${#message}
  border=$(yes "-" | tr -d '\n' | head -c "$length")

  echo "$border"
  echo "$message"
  echo "$border"

  exit
elif ! [[ -v POSITIONAL_ARGS[0] ]]; then
  curl -sSOL "https://github.com/ljp-projects/TeaScript/releases/download/$tea_version/TeaScript.jar"
else
  mv "${POSITIONAL_ARGS[0]}" ./
fi

touch tea
echo "java -jar /usr/local/bin/TeaScript.jar \$*" > tea

touch tea-version
echo "$tea_version" > tea-version

chmod +x tea

message="TeaScript $tea_version has been installed successfully!"
length=${#message}
border=$(yes "-" | tr -d '\n' | head -c "$length")

echo "$border"
echo "$message"
echo "$border"