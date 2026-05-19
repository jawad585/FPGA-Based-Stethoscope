# FPGA-Based Stethoscope

This repository contains the source code and generated assets for the FPGA-based stethoscope project.

## Repository Contents

- Java mobile app code for Bluetooth LE communication and terminal control.
- Arduino Mega sketch used for the hardware-side interface.
- FPGA design code, along with the generated bitstream and build outputs.

## Mobile App Setup

The mobile app lives under `mobile/`.
That folder already includes its own setup and build instructions, so use the documentation there when preparing the Android environment.

## Included Projects

- `mobile/SimpleBluetoothLeTerminal/`: Android Bluetooth LE terminal app, included in this repository after local updates.
- `sketch/`: Arduino Mega sketch files.
- `uart_pipeline/`: FPGA project files and generated bitstream artifacts.

## Notes

The mobile app folder is a cloned copy of the existing `SimpleBluetoothLeTerminal` project, kept here as part of this repository.