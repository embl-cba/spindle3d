<img src="./doc/images/icon-with-text.png" width="800">

A Fiji plugin for the automated measurement of 3D morphological parameters of volumetric images of mitotic spindles.

## Install

- Download [Fiji](https://fiji.sc/)
  - Not necessary if you already have Fiji on your computer
- Start Fiji
  - Install the `Spindle3D` update site in Fiji: [ Help > Update > Manage Update Sites ]
    - If the Spindle3D update site does not appear in the list you can add it manually:
      - [ Add update site ] and enter
      - Name: Spindle3D | URL: https://sites.imagej.net/Spindle3D/
- Restart Fiji

## Start

Open Fiji and type `Spindle` in the search bar.

<img src="./doc/images/plugin.png" width="600">

Select one of the two plugins. See below for details.

## Spindle3D...

Information to come...


## Spindle3D Advanced...

Information to come...

## Methods

### Spindle segmentation

The spindle is segmented by means of applying an intensity threshold on the tubulin signal.
The threshold is computed as follows (the actual implementation is sightly different but the result is mathematically identical):
1. The DNA mask is expanded by one pixel and the original DNA mask is subtracted from the expanded mask, yielding a new "DNA periphery mask"
   - Since the DNA metaphase is always (in all the images that we observed) substantially wider than the spindle, the DNA periphery mask contains a comparable amount of pixels that are (i) within the spindle and (ii) outside the spindle, but still within the cell.
1. All tublin intensities within the DNA periphery mask are measured and subjected to an Otsu thresholding algorithm. The resulting threshold is used to binarise the tubulin image.
   - The Otsu algorithm is very sensitive to the fraction of pixels that are supposed to be above the sought for threshold. Thus, the image region to which the algorithm is applied is critical. For example, applying the algorithm to the whole image likely could result in a threshold that separates well the cytoplasmic tubulin intensity from the image background, rather than the spindle (polymerised) tubulin intensity from the cytoplasmic (monomeric) tubulin intensity. The reason being that the fraction of pixels that are within the spindle is very small compared to the whole image and could thus be "neglected" by the algorithm. Our method of measuring an Otsu threshold in the immediate periphery of the DNA only, tackles these challenges by ensuring that all considered intensity values are inside the one and same cell and comprise (to a similar fraction) cytoplasmic and spindle tubublin. However, within the spindle there could be pixels reflecting various degress of tubulin density (degree of bundling and polymerisation) and in addition to the diffraction limit there always will be a gradient of intensity values that does not reflect any biological significance but simply the point spread function of the microscope. The Otsu thresholding method does nothing to  explicitly deal with these issues, however by visual inspection we (a) found the result to appear satisfactory (TODO: can we say more?), and (b) the method does have the benefit of being mathematically clearly defined and thereby being objective and reproducible.
1. The binarised tubulin image is further processed (TODO) yielding the final spindle mask.
