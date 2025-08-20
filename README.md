> [!CAUTION]
> This plugin is not maintained anymore.
> We recommend to use [QuPath](https://qupath.github.io) to handle whole slide images. 

# NanoZoomer-J

NanoZoomer-J is a small collection of [ImageJ][imagej] plugins to deal with the 
NDPI image file format. 
The `NDPI Converter > [...] OME-TIF Converter` converter uses [Bio-Formats][bf] to convert the proprietary 
file format to the `ome.tif` without loading the entire file into memory, 
which is important given that ndpi-files may be too big for the computer's memory.

## Installation
The packaged binaries can be found on the [release-page][release]. To install the plugin 
in ImageJ, the downloaded jar-file can be dragged and dropped on the ImageJ main UI, or via the 
main menu: Plugins > Install PlugIn...

## Usage
Once the jar has been added to ImageJ, the plugins can be run via the menu:
`Plugins > NDPI Converter > ...`

## Batch Converter
The batch converter takes an input directory, searches it for `ndpi` files and then lets you choose the channels and the pixel size for conversion. Once the directory has been selected, it takes a short moment
to read the metadata from the first `ndpi` file. The order of the channels can be chosen by sequentially clicking on them in the selection table. All the channels are then merged into a single multichannel `ome.tif`.

> Note: When converting a bright-field image, it treats the RGB as channels. So when opening the resulting `ome.tif` we get a color stack.


[imagej]: http://imagej.net
[bf]: http://www.openmicroscopy.org/site/products/bio-form…
[release]: https://github.com/fmeyenhofer/NanoZoomer-J/releases
