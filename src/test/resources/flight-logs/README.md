# Flight-log fixtures

The `dji-*.srt` files were downloaded from the public sample directory of
[JuanIrache/DJI_SRT_Parser](https://github.com/JuanIrache/DJI_SRT_Parser/tree/b6ed7901b0f77dc8864eaeaf153914482bfba6da/samples)
under its [MIT license](https://github.com/JuanIrache/DJI_SRT_Parser/blob/b6ed7901b0f77dc8864eaeaf153914482bfba6da/LICENSE).
Copyright notice from the upstream license: `Copyright (c) 2018 JuanIrache`.
They are samples supplied by that project, not evidence for a SmartDroneInspection flight.
`dji-mavic-3-altitudes.srt` contains three representative lines from the upstream
`MAVIC3.srt` sample at the same revision, demonstrating its `rel_alt` and `abs_alt`
fields. Its original timestamp has no timezone and must not be treated as UTC.

`airdata-sample.csv`, `mission-sample.kml`, `mission-sample.gpx`, and
`kml-gx-track.kml` are synthetic fixtures created for this project. The
AirData-like CSV header is based on the public
[Airdata CSV parser](https://github.com/quinntysonrpas/skylogr/blob/main/backend/parsers/airdata_csv_parser.py),
not an independently verified export from a particular flight or aircraft.
