run("Close All");
open("/Users/tischer/Documents/spindle3d/src/test/resources/publication/20201209_R1E309_TubGFP_DM1a_KATNA1_D0_011-3-MAX-DNA.tif");
rename("input");

selectWindow("input");
makeRectangle(50, 29, 63, 120);
run("Duplicate...", "title=1");
run("Auto Threshold", "method=Otsu white");
//run("Select None");

selectWindow("input");
makeRectangle(36, 13, 92, 147);
run("Duplicate...", "title=2");
run("Auto Threshold", "method=Otsu white");
//run("Select None");

selectWindow("input");
run("Select None");
run("Duplicate...", "title=3");
run("Auto Threshold", "method=Otsu white");

run("Tile");