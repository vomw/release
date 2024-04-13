FROM ubuntu:latest

RUN apt-get update && apt-get install -y build-essential git wget gcc-arm-linux-gnueabihf

# Download and extract the Raspberry Pi toolchain
# RUN wget https://github.com/raspberrypi/tools/archive/refs/tags/$(curl -s https://api.github.com/repos/raspberrypi/tools/tags | grep -oP '"name": "\K(.*)(?=")' | head -n 1).tar.gz
# RUN tar -xf $(curl -s https://api.github.com/repos/raspberrypi/tools/tags | grep -oP '"name": "\K(.*)(?=")' | head -n 1).tar.gz

# ENV PATH="/tools-$(curl -s https://api.github.com/repos/raspberrypi/tools/tags | grep -oP '"name": "\K(.*)(?=")' | head -n 1)/arm-bcm2708/gcc-linaro-arm-linux-gnueabihf-raspbian-x64/bin:${PATH}"

WORKDIR /workspace
