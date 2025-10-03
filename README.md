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

### JpegEncoder()

Added subsampling enum & parameter, setting sampling factors based on the chosen scheme.

### DCT Class

Removed unused forwardDCTExtreme() method.

Integrated quantization table scaling into Quantizer class.

### compress()

Streamlined compression flow into single public method.

### JpegInfo Class

Added Subsampling enum support with three schemes: YUV_444 (4:4:4), YUV_422 (4:2:2), YUV_420 (4:2:0).

Sets horizontal and vertical sampling factors automatically based on selected scheme.

Simplified padding calculation using bitwise operations.

Improved convertToYCbCr() method with cleaner RGB-to-YCbCr conversion & chroma downsampling.

### DCT Class

Removed unused forwardDCTExtreme() method.

Integrated quantization table scaling into Quantizer class.

Cleaner forwardDCT() implementation using AAN algorithm.

### Quantizer Class (New)

Extracted quantization logic into dedicated class.

Provides quantizeBlock() method for DCT coefficient quantization.

Maintains separate luminance and chrominance quantization tables.

### Huffman Class

Pre-builds Huffman lookup tables in constructor for efficiency.

Simplified encodeBlock() method with cleaner DC differential and AC run-length encoding.

Removed Vector-based storage in favor of direct array access.

Eliminated redundant initHuf() complexity by using HuffmanTable helper class.

### HuffmanTable Class (New)

Encapsulates Huffman code generation from bit lengths and values.

Pre-calculates lookup tables for fast encoding.

### BitStream Class (New)

Handles all bit-level output operations.

Manages byte stuffing automatically.

Cleaner flush() method for remaining bits.
