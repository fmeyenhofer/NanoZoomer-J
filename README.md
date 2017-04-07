# NanoZoomer-J

NanoZoomer-J is a collection of [ImageJ][imagej] plugins to deal with the 
NDPI image file format. 
The "NDPI 2 OME-TIF" converter uses [Bio-Formats][bf] to convert the propriatary 
file format to the open ome-tif without loading the entire file into memory, 
which is important given that ndpi-files may contain a lot of data.

# Installation

The packaged binaries can be found on the [release-page][release]. To install the plugin 
in ImageJ, the downloaded jar-file can be dragged and dropped on the ImageJ main UI, or via the 
main menu: Plugins > Install PlugIn... 

[imagej]: http://imagej.net
[bf]: http://www.openmicroscopy.org/site/products/bio-form…
[release]: https://github.com/Meyenhofer/NanoZoomer-J/releases