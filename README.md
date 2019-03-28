# NanoZoomer-J

NanoZoomer-J is a small collection of [ImageJ][imagej] plugins to deal with the 
NDPI image file format. 
The "NDPI 2 OME-TIF" converter uses [Bio-Formats][bf] to convert the proprietary 
file format to the open ome-tif without loading the entire file into memory, 
which is important given that ndpi-files may contain a lot of data.

# Installation

The packaged binaries can be found on the [release-page][release]. To install the plugin 
in ImageJ, the downloaded jar-file can be dragged and dropped on the ImageJ main UI, or via the 
main menu: Plugins > Install PlugIn... 


# Usage

Once the jar has been added to ImageJ, the plugins can be run via the menu:
`Plugins > NanoZooomer NDPI > ...`

## Batch Converter

The batch converter takes an input directory, searches it for NDPI files and then lets you choose the 
channels and the pixel size to convert. Once the directory has been selected, it takes a short moment
to read the meta data from the first NDPI file. The order of the channels can be chosen by 
sequentially clicking on them in the selection table. All the channels are then merged into
a single multi-channel OME-TIF.


[imagej]: http://imagej.net
[bf]: http://www.openmicroscopy.org/site/products/bio-form…
[release]: https://github.com/fmeyenhofer/NanoZoomer-J/releases