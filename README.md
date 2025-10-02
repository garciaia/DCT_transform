# DCT Transform

Compresses images using JPEG compression, allowing for the selection of quality and subsampling, and compares the file sizes. 

## Convert.java 

### runCompression()

Loads in provided image, compresses it, and calculates the amount of time compression takes.

Prints the compression time, the file sizes pre- and post-compression, and the ratio of compression.

Subsampling parameter options: YUV_420, YUV_422, YUV_444 (also default).

Quality: variable between 1-100. A smaller value corresponds to worse quality & greater compression. 
If value is outside of this range, it is clamped down to the nearest value within the range.

### saveAsBmp()

Takes in JPEG file and converts it to a BMP image under a new, given file name.

### createDirectoryIfNotExists()

Creates directory in given path if it does not already exist.

## JpegEncoder.java Changes

### JpegEncoder() / JpegInfo()

Added subsampling enum & parameter, setting sampling factors based on the chosen scheme.

### DCT Class

Removed unused forwardDCTExtreme() method.
Integrated quantization table scaling into Quantizer class.

### compress()

Streamlined compression flow into single public method.

### Quantizer Class (New)

Extracted quantization logic into dedicated class.
Provides quantizeBlock() method for DCT coefficient quantization.
Maintains separate luminance and chrominance quantization tables.

### HuffmanTable Class (New)

Encapsulates Huffman code generation from bit lengths and values.
Pre-calculates lookup tables for fast encoding.

### BitStream Class (New)

Handles all bit-level output operations.
Manages byte stuffing (0x00 after 0xFF) automatically.
Cleaner flush() method for remaining bits.
