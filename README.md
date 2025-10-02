# DCT Transform
Compresses images using JPEG compression, allowing for the selection of quality and subsampling, and compares the file sizes. 

## runCompression()
Loads in provided image, compresses it, and calculates the amount of time compression takes.

Prints the compression time, the file sizes pre- and post-compression, and the ratio of compression.

Subsampling parameter options: YUV_420, YUV_422, YUV_444 (also default).

Quality: variable between 1-100. A smaller value corresponds to worse quality & greater compression. 
If value is outside of this range, it is clamped down to the nearest value within the range.

## saveAsBmp()
Takes in JPEG file and converts it to a BMP image under a new, given file name.

## createDirectoryIfNotExists()
Creates directory in given path if it does not already exist.
