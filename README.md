# TypeStateMisuseDetector
Finite state machines are useful for modelling complex flows and tracking states in software systems. Android is no exception, which provides staggering numbers of typestate APIs to perform state transition operations. The wide adoption of the typestate APIs coexists with inappropriate usage, leading to software defects and poor user experience. However, little is known about typestate usage in real-world Android applications and the impact of their misuse. To mitigate this research gap, we first conduct a preliminary study on the Android official documents (i.e., from API level 1 to 31) to extract typestate rules that Android apps should comply with. Then, we propose a novel prototype tool, \tool{}, to detect typestate misuses by performing inter-procedural data-flow analysis that checks a given Android app for violations of the found typestate rules. Experimental results on thousands of real-world Android apps show that \tool{} is effective in detecting typestate misuses in Android applications, including both incompatibility and inappropriate usage. We also show that \tool{} can outperform state-of-the-art API misuse detectors and dynamic testing techniques in pinpointing typestate misuses.

[//]: # (Our paper has been accepted at ASE 2022.)

## Approach


## Setup
The following is required to set up JUnitTestGen:
* MAC system
* IntelliJ IDEA

##### Step 1: Load dependencies to your local repository
* git clone git@github.com:SMAT-Lab/JUnitTestGen.git
* cd junittestgen
* ./res/loadDependencies.sh

##### Step 2: build package：
mvn clean install

##### Step 3: example of running JUnitTestGen(3 parameters):
* Parameters are needed here: [your_apk_path.apk],[path of android platform],[path of result.csv]
* Example: your_path/905a4f82bc194334a046afa9bf29eed7.apk, ~/Library/Android/sdk/platforms, your_path/result.csv
       
## Output
* Refer to sootOutput/ folder to check all the generated test cases.
* Refer to [path of result.csv] to check the map of test case name and its corresponding targetAPI.
