# TeaScript

![TeaScript logo](src/TeaScript.svg)

## Installation

### Requirements

- Java SE 8+
- Standard POSIX command-line tools
- `jq`

### Instructions

| flag                  | value                              | description                                             |
|-----------------------|------------------------------------|---------------------------------------------------------|
| -t or --tag           | The desired tag.                   | Install with JAR from the specified tag.                |
| -l or --legacy-branch | The desired legacy branch.         | Install via installer.sh from specified branch.         |
| -c or --commit        | The desired commit (short or full) | Install via installer.sh from specified commit.         |
| -k or --keep          | N/A                                | Preserve any files that you specify may be changed.     |
| none                  | N/A                                | Install with the latest JAR available in the downloads. |

1. Download the TeaScript.jar file from the desired release
2. Run `curl https://raw.githubusercontent.com/ljp-projects/TeaScript/main/src/installer.sh | sudo bash -s options`.
3. Run `tea version` to verify the installation.

### Instructions (legacy)

**THERE IS NO GUARANTEE THAT LEGACY VERSIONS OF TEASCRIPT WILL BE FUNCTIONAL.**

1. Run `curl https://raw.githubusercontent.com/ljp-projects/TeaScript/TAG_NAME/src/installer.sh | sh`
2. Verify installation with `tea version`

If you want to you can also locate the raw URL for a specific commit of the installer.sh file.