# Inspector
Usage Instruction:
As the tool is far from completed. There are only two functions available for now. 
Sort all serializable classes in a war file and detect serialization through ois.readObject method.

After git cloning the repository down to your local machine, open the project using eclipse.
To use the first function uncomment the main method in SortClass.java. 
Next change the argument in getSerialClasses to the absolute path of the war file you wish to scan.
(Optional) Go to Unpack.java change the value of warDir to the directory you wish the war file to be unzipped to.
Run SortClass the list of serializable classes and their compiled bytes should appear in the console.

To use the second function, delete the unzipped war file directory.
Next run checkForSerialization to see the list of classes which called the ois.readObject method. 
