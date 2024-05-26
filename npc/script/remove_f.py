#!/usr/bin/env python3

import re
from argparse import ArgumentParser

RE_BANNER = re.compile(r"^// ----- 8< ----- FILE \"((?:.+)\.(?:.+))\" ----- 8< -----$")
RE_FILE_F = re.compile(r"^.+\.f$")


def match_banner(line: str) -> str | None:
    match RE_BANNER.match(line):
        case None:
            return None
        case m:
            return m.group(1)


parser = ArgumentParser(
    description="Remove .f content from SystemVerilog files generated by Chisel"
)
parser.add_argument(
    "filenames",
    metavar="FILE",
    type=str,
    nargs="+",
    help="SystemVerilog files to process",
)
args = parser.parse_args()

for filename in args.filenames:
    with open(filename, "rt") as f:
        lines = f.readlines()

    with open(filename, "wt") as f:
        in_f_file = False
        for banners in enumerate(map(match_banner, lines)):
            if banners[1] is None:
                pass
            elif RE_FILE_F.match(banners[1]) is None:
                in_f_file = False
            else:
                in_f_file = True
            if not in_f_file:
                print(lines[banners[0]], end="", file=f)
