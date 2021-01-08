inputDir = "/Users/tischer/Documents/spindle3d/src/test/resources/multiple-dna";
outputDir = "/Users/tischer/Desktop/Desktop";

fileList = getFileList(inputDir);

for (i = 0; i < fileList.length; i++) 
        {
	fileName = inputDir + "/" +  fileList[i];
	print("Opening next: " + fileName);
	open(fileName);	
 
	run("Duplicate...", "duplicate channels=1");
	rename("Channel_1");
	run("32-bit");
	run("Z Project...", "projection=[Median]");
	List.setMeasurements("limit");
	median_channel_1 = List.getValue("Median");
	stdev_channel_1 = List.getValue("StdDev");
	print("Median of channel 1= " + median_channel_1);
	print("StDev of channel 1 (TODO MAD?!)= " + stdev_channel_1);
	selectWindow("Channel_1");
	run("Subtract...", "value=median_channel_1 stack");
	run("Divide...", "value=stdev_channel_1 stack");
	

	selectWindow(fileList[i]);
	run("Duplicate...", "duplicate channels=2");
	rename("Channel_2");
	run("32-bit");
	run("Z Project...", "projection=[Median]");
	List.setMeasurements("limit");
	median_channel_2 = List.getValue("Median");
	stdev_channel_2 = List.getValue("StdDev");
	print("Median of channel 2= " + median_channel_2);
	print("StDev of channel 2 (TODO MAD?!)= " + stdev_channel_2);
	selectWindow("Channel_2");
	run("Subtract...", "value=median_channel_2 stack");
	run("Divide...", "value=stdev_channel_2 stack");
	

	imageCalculator("Min create stack", "Channel_1", "Channel_2");
	rename("MIN");
	imageCalculator("Multiply create stack", "MIN", "Channel_1");
	
	saveAs("tiff", outputDir+"/Minimum_MultiplyTub_"+fileList[i]);
	
	run("Close All");
        }
print("Done.");