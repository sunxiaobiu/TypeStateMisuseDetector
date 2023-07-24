# TypeStateMisuseDetector
Android provides a large number of typestate rules to support
complex tasks. For example, a method must be called before another
method is called or it can only be called once a given resource is
in a certain state. Such rules are often supported through typestate
APIs. Unfortunately, these typestate APIs might be misused by app
developers, leading to software defects and subsequently poor user
experiences. Little is currently known about typestate usage or
misuse in real-world Android apps. Hence we propose a novel
research tool, DroidTEC, which aims at detecting typestate misuses
through path-sensitive and inter-procedural data-flow analysis. We
first conducted a preliminary study to mine typestate rules offered
by Android and harvested a total of 736 distinct typestate rules.
We then design and implement DroidTEC to scan Android apps by
checking if they have violated any of the aforementioned typestate
rules. Experimental results on 10,000 Android apps from Google
Play show that DroidTEC is effective in detecting typestate misuses
in Android applications – it locates 88,732 misuses with an accuracy
of 87.3%. DroidTEC also outperforms the state-of-the-art typestate
analyzers and resource leak detectors when applied to pinpointing
typestate misuses.

[//]: # (Our paper has been accepted at ASE 2022.)

## Approach


## Setup
The following is required to set up DroidTEC:
* MAC system
* IntelliJ IDEA

##### Step 1: Load dependencies to your local repository
* git clone
* cd TypeStateMisuseDetector

##### Step 2: build package：
mvn clean install

##### Step 3: example of running DroidTEC(3 parameters):
* Parameters are needed here: [your_apk_path.apk],[path of android platform]
* Example: your_path/xxx.apk, ~/Library/Android/sdk/platforms
       
## Output
* Refer to the output to check all the misuse cases.
