# capstone-mpi

Download java 8
Download OpenMPI http://www.open-mpi.org/faq/?category=java
./configure --enable-mpi-java
make
sudo make install
download Kepler
Download JDT patch 
https://wiki.eclipse.org/JDT/Eclipse_Java_8_Support_For_Kepler
run lombak on kepler
add mpi jar to reference (usr/local/lib)
guide: http://users.dsic.upv.es/~jroman/preprints/ompi-java.pdf

run code:
mpirun -np 8 java -cp /ca/mcmaster/capstone/program/*:/ca/mcmaster/capstone/monitoralgorithm/*:/ca/mcmaster/capstone/util/*:/ca/mcmaster/capstone/initializer/*:/ca/mcmaster/capstone/logger/*:../../libs/*  ca.mcmaster.capstone.program.Node
