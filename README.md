# Code for Skeleton Extraction
The code for `Curve Skeleton Extraction via k–Nearest–Neighbors Based Contraction`

## Usage
Dev Environment:
- Java 8
- Maven 


Run:
```bash
mvn clean compile assembly:single 
cd target
java -jar .\mpp-1.0-SNAPSHOT-jar-with-dependencies.jar
```

If everything goes well, you will see:
![](https://jimmie00x0000.github.io/img/MPP-Snapshot.png)


Click `...` button to import point cloud(ply format is ok), then click `Run` button to start the skeletonization process. 

We use Java3d to visualize the point clouds and the skeletons. Because Java3d has been unmaintained for a long time, this 
program may not work on some operating systems.

## About
If this project contributes to an academic publication, cite it as:
```bibtex
@article{zhou2020curve,
  title={Curve Skeleton Extraction Via K-Nearest-Neighbors Based Contraction},
  author={Zhou, Jianling and Liu, Ji and Zhang, Min},
  journal={International Journal of Applied Mathematics and Computer Science},
  volume={30},
  number={1},
  pages={123--132},
  year={2020},
  publisher={Sciendo}
}
```


