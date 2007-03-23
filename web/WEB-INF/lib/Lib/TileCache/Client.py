#!/usr/bin/python
# BSD Licensed, Copyright (c) 2006 MetaCarta, Inc.

import sys, urllib, urllib2, time

class WMS (object):
    fields = ("bbox", "srs", "width", "height", "format", "layers", "styles")
    defaultParams = {'version': '1.1.1', 'request': 'GetMap', 'service': 'WMS'}
    __slots__ = ("base", "params", "client", "data", "response")

    def __init__ (self, base, params):
        self.base    = base
        if self.base[-1] not in "?&":
            if "?" in self.base:
                self.base += "&"
            else:
                self.base += "?"

        self.params  = {}
        self.client  = urllib2.build_opener()
        for key, val in self.defaultParams.items():
            self.params[key] = val
        for key in self.fields:
            if params.has_key(key):
                self.params[key] = params[key]
            else:
                self.params[key] = ""

    def url (self):
        return self.base + urllib.urlencode(self.params)
    
    def fetch (self):
        urlrequest = urllib2.Request(self.url())
        # urlrequest.add_header("User-Agent",
        #    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)" )
        try:
            response = self.client.open(urlrequest)
            data = response.read()
        except urllib2.HTTPError, err:
            response = err
            data = None
        except urllib2.URLError, err:
            err.code = -1
            response = err
            data = None
        return data, response

    def setBBox (self, box):
        self.params["bbox"] = ",".join(map(str, box))

def seed (base, layer, levels = (0, 5), bbox = None):
    from Layer import Tile
    params = { 'layers' : layer.layers,
               'srs'    : layer.srs,
               'width'  : layer.size[0],
               'height' : layer.size[1],
               'format' : layer.format() }
    client = WMS(base, params)

    if not bbox: bbox = layer.bbox

    start = time.time()
    total = 0
    
    for z in range(*levels):
        bottomleft = layer.getClosestCell(z, bbox[0:2])
        topright   = layer.getClosestCell(z, bbox[2:4])
        print >>sys.stderr, "###### %s, %s" % (bottomleft, topright)
        zcount = 0 
        ztiles = (topright[1] - bottomleft[1] + 1) * (topright[0] - bottomleft[0] + 1)
        for y in range(bottomleft[1], topright[1] + 1):
            for x in range(bottomleft[0], topright[0] + 1):
                tileStart = time.time()
                tile = Tile(layer,x,y,z)
                bounds = tile.bounds()
                client.setBBox(bounds)
                client.fetch()
                total += 1
                zcount += 1
                box = "(%.4f %.4f %.4f %.4f)" % bounds
                print >>sys.stderr, "%02d (%06d, %06d) = %s [%.4fs : %.3f/s] %s/%s" \
                     % (z,x,y, box, time.time() - tileStart, total / (time.time() - start + .0001), zcount, ztiles)

if __name__ == '__main__':
    from Layer import Layer
    base  = sys.argv[1]
    layer = Layer(sys.argv[2])
    if len(sys.argv) == 5:
        seed(base, layer, map(int, sys.argv[3:]))
    elif len(sys.argv) == 6:
        seed(base, layer, map(int, sys.argv[3:5]), map(float, sys.argv[5].split(",")))
    else:
        for line in sys.stdin.readlines():
            lat, lon, delta = map(float, line.split(","))
            bbox = (lon - delta, lat - delta, lon + delta, lat + delta)
            print >>sys.stderr, "===> %s <===" % (bbox,)
            seed(base, layer, (5, 17), bbox)
