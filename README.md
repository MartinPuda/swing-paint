# swing-paint

MS paint in Clojure + Swing. Start drawing with:

```
(-main)
```

### Notes

- For inserting text, choose Text tool, click into canvas, write your text into popup window (you can also change size and font) and then close that window.


- Canvas is always saved as 1820 x 894 image.


- For drawing curve, choose Curve tool and click four times into canvas. Curve is Bezier curve with *start - control point 1 - control point 2 - end* in given points.

### Missing tools

- Free-form select
- Select
- Magnifier

### Original commit
29 Dec 2021

## Usage

    $ java -jar swing-paint-0.1.0-standalone.jar [args]

## License

Copyright © 2021 - 2023

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
