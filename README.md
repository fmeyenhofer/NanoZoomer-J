#NanoZoomer-J
NanoZoomer-J is a collection of [ImageJ][imagej] plugins to deal with the 
NDPI image file format. 
The "NDPI 2 OME-TIF" converter uses [Bio-Formats][bf] to convert the propriatary 
file format to the open ome-tif without loading the entire file into memory, 
which is important given that ndpi-files may contain a lot of data.


[imagej]: http://imagej.net
[bf]: http://www.openmicroscopy.org/site/products/bio-form…