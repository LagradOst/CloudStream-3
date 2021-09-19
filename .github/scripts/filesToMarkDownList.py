#!/usr/bin/env python3
from sys import argv

template = "- {}"
output = []

for file in argv[1:]:
  output += [template.format(file)]

print("\n".join(output))
