//
// Javascript for GODIVA2 page.
//

var map = null;
var layerName = '';
var prettyDsName = ''; // The dataset name, formatted for human reading
var zPositive = 0; // Will be 1 if the selected z axis is positive
var tValue = null;
var prettyTValue = null; // The t value, formatted for human reading
var isIE;
var scaleMinVal;
var scaleMaxVal;
var timestep = 0;
var newVariable = true;  // This will be true when we have chosen a new variable
var essc_wms = null; // The WMS layer for the ocean data
var autoLoad = null; // Will contain data for auto-loading data from a permalink
var bbox = null; // The bounding box of the currently-displayed layer
var featureInfoUrl = null; // The last-called URL for getFeatureInfo (following a click on the map)

// Ajax call using the Prototype library
// url: The URL of the data source
// params: The parameters to append to the URL
// onsuccess: A function that will be called with the original request object
function downloadUrl(url, params, onsuccess)
{
    var myAjax = new OpenLayers.Ajax.Request(
        url, 
        {
            method: 'get', 
            parameters: params, 
            onComplete: onsuccess
        });
}

// Called when the page has loaded
window.onload = function()
{
    // reset the scale markers
    $('scaleMax').value = '';
    $('scaleMin').value = '';

    // Make sure 100% opacity is selected
    $('opacityValue').value = '100';

    // Detect the browser (IE6 doesn't render PNGs properly so we don't provide
    // the option to have partial overlay opacity)
    isIE = navigator.appVersion.indexOf('MSIE') >= 0;

    // Stop the pink tiles appearing on error
    OpenLayers.Util.onImageLoadError = function() {  this.style.display = ""; this.src="./images/blank.png"; }
    
    // Set up the OpenLayers map widget
    map = new OpenLayers.Map('map');
    var ol_wms = new OpenLayers.Layer.WMS( "OpenLayers WMS", 
        "http://labs.metacarta.com/wms-c/Basic.py?", {layers: 'basic', format: 'image/png' } );
    var bluemarble_wms = new OpenLayers.Layer.WMS( "Blue Marble", 
        "http://labs.metacarta.com/wms-c/Basic.py?", {layers: 'satellite' } );
    var osm_wms = new OpenLayers.Layer.WMS( "Openstreetmap", 
        "http://labs.metacarta.com/wms-c/Basic.py?", {layers: 'osm-map' } );
    var human_wms = new OpenLayers.Layer.WMS( "Human Footprint", 
        "http://labs.metacarta.com/wms-c/Basic.py?", {layers: 'hfoot' } );
        
    // ESSI WMS (see Stefano Nativi's email to me, Feb 15th)
    /*var essi_wms = new OpenLayers.Layer.WMS.Untiled( "ESSI WMS", 
        "http://athena.pin.unifi.it:8080/ls/servlet/LayerService?",
        {layers: 'sst(time-lat-lon)-T0', transparent: 'true' } );
    essi_wms.setVisibility(false);*/
            
    // The SeaZone Web Map server
    var seazone_wms = new OpenLayers.Layer.WMS1_3("SeaZone bathymetry", "http://ws.cadcorp.com/seazone/wms.exe?",
        {layers: 'Bathymetry___Elevation.bds', transparent: 'true'});
    seazone_wms.setVisibility(false);
    
    map.addLayers([bluemarble_wms, ol_wms, osm_wms, human_wms, seazone_wms/*, essi_wms*/]);
    
    // Make sure the Google Earth and Permalink links are kept up to date when
    // the map is moved or zoomed
    map.events.register('moveend', map, setGEarthURL);
    map.events.register('moveend', map, setPermalinkURL);
    
    // If we have loaded Google Maps and the browser is compatible, add it as a base layer
    if (typeof GBrowserIsCompatible == 'function' && GBrowserIsCompatible()) {
        var gmapLayer = new OpenLayers.Layer.Google("Google Maps (satellite)", {type: G_SATELLITE_MAP});
        var gmapLayer2 = new OpenLayers.Layer.Google("Google Maps (political)", {type: G_NORMAL_MAP});
        map.addLayers([gmapLayer, gmapLayer2]);
    }
        
    map.addControl(new OpenLayers.Control.LayerSwitcher());
    //map.addControl(new OpenLayers.Control.MousePosition({prefix: 'Lon: ', separator: ' Lat:'}));
    map.zoomTo(1);
    
    // Add a listener for changing the base map
    //map.events.register("changebaselayer", map, function() { alert(this.projection) });
    // Add a listener for GetFeatureInfo
    map.events.register('click', map, getFeatureInfo);
    
    var filter = '';
    // see if we are recreating a view from a permalink
    if (window.location.search != '') {
        autoLoad = new Object();
        autoLoad.dataset = null;
        autoLoad.variable = null;
        autoLoad.zValue = null;
        autoLoad.tValue = null;
        autoLoad.bbox = null;
        autoLoad.scaleMin = null;
        autoLoad.scaleMax = null;
        // strip off the leading question mark
        var queryString = window.location.search.split('?')[1];
        var kvps = queryString.split('&');
        for (var i = 0; i < kvps.length; i++) {
            keyAndVal = kvps[i].split('=');
            if (keyAndVal.length > 1) {
                var key = keyAndVal[0].toLowerCase();
                if (key == 'dataset') {
                    autoLoad.dataset = keyAndVal[1];
                } else if (key == 'variable') {
                    autoLoad.variable = keyAndVal[1];
                } else if (key == 'elevation') {
                    autoLoad.zValue = keyAndVal[1];
                } else if (key == 'time') {
                    autoLoad.tValue = keyAndVal[1];
                } else if (key == 'bbox') {
                    autoLoad.bbox = keyAndVal[1];
                } else if (key == 'scale') {
                    autoLoad.scaleMin = keyAndVal[1].split(',')[0];
                    autoLoad.scaleMax = keyAndVal[1].split(',')[1];
                } else if (key == 'filter') {
                    // we must adapt the site for this brand (e.g. by showing only
                    // certain datasets)
                    filter = keyAndVal[1];
                    if (filter == 'MERSEA' || filter == 'ECOOP') {
                        // If we're viewing through the MERSEA or ECOOPpage, the logo
                        // is displayed in the header and footer so we blank it
                        // out here.
                        $('jcommlogo').src = 'images/blank.png';
                        $('jcommlogo').alt = '';
                        $('jcommlogo').width = 133;
                        $('jcommlogo').height = 30;
                        $('jcommlink').href = '';
                    }
                }
            }
        }
    }       

    // Load the list of datasets to populate the left-hand menu
    loadDatasets('accordionDiv', filter);
}

// Event handler for when a user clicks on a map
function getFeatureInfo(e)
{
    if (essc_wms != null)
    {
        $('featureInfo').innerHTML = "Getting feature info...";
        featureInfoUrl = essc_wms.getFullRequestString({
            REQUEST: "GetFeatureInfo",
            BBOX: essc_wms.map.getExtent().toBBOX(),
            I: e.xy.x,
            J: e.xy.y,
            INFO_FORMAT: 'text/xml',
            QUERY_LAYERS: essc_wms.params.LAYERS,
            WIDTH: essc_wms.map.size.w,
            HEIGHT: essc_wms.map.size.h
            });
        OpenLayers.loadURL(featureInfoUrl, '', this, gotFeatureInfo);
        Event.stop(e);
    }
}

// Called when we have received some feature info
function gotFeatureInfo(response)
{
    var xmldoc = response.responseXML;
    var lon = xmldoc.getElementsByTagName('longitude')[0];
    var lat = xmldoc.getElementsByTagName('latitude')[0];
    var val = xmldoc.getElementsByTagName('value')[0];
    if (lon) {
        $('featureInfo').innerHTML = "<b>Lon:</b> " + toNSigFigs(lon.firstChild.nodeValue, 4) + 
            "&nbsp;&nbsp;<b>Lat:</b> " + toNSigFigs(lat.firstChild.nodeValue, 4) + "&nbsp;&nbsp;<b>Value:</b> " +
            toNSigFigs(val.firstChild.nodeValue, 4);
        if (timeSeriesSelected()) {
            // Construct a GetFeatureInfo request for the timeseries plot
            // Get a URL for a WMS request that covers the current map extent
            var urlEls = featureInfoUrl.split('&');
            // Replace the parameters as needed.  We generate a map that is half the
            // width and height of the viewport, otherwise it takes too long
            var newURL = urlEls[0];
            for (var i = 1; i < urlEls.length; i++) {
                if (urlEls[i].startsWith('TIME=')) {
                    newURL += '&TIME=' + $('firstFrame').innerHTML + '/' + $('lastFrame').innerHTML;
                } else if (urlEls[i].startsWith('INFO_FORMAT')) {
                    newURL += '&INFO_FORMAT=image/png';
                } else {
                    newURL += '&' + urlEls[i];
                }
            }
            // Image will be 400x300, need to allow a little elbow room
            $('featureInfo').innerHTML += "&nbsp;&nbsp;<a href='#' onclick=popUp('"
                + newURL + "',450,350)>Create timeseries plot</a>";
        }
    } else {
        $('featureInfo').innerHTML = "Can't get feature info data for this layer <a href=\"javascript:popUp('whynot.html', 200, 200)\">(why not?)</a>";
    }
}

function popUp(url, width, height)
{
    day = new Date();
    id = day.getTime();
    window.open(url, id, 'toolbar=0,scrollbars=0,location=0,statusbar=0,menubar=0,resizable=1,width='
        + width + ',height=' + height + ',left = 300,top = 300');
}

// Populates the left-hand menu with a set of datasets
function loadDatasets(dsDivId, filter)
{
    downloadUrl('wms', 'REQUEST=GetMetadata&item=datasets&filter=' + filter,
        function(req) {
            $(dsDivId).innerHTML = req.responseText;
            var accordion = new Rico.Accordion (
                dsDivId,
                { onShowTab: datasetSelected, panelHeight: 200 }
            );
            var foundAuto = false;
            if (autoLoad != null && autoLoad.dataset != null) {
                // We are automatically loading a dataset from a permalink
                for (var i = 0; i < accordion.accordionTabs.length && !foundAuto; i++) {
                    if (autoLoad.dataset == accordion.accordionTabs[i].titleBar.id) {
                        foundAuto = true;
                        accordion.showTab(accordion.accordionTabs[i]);
                    }
                }
            }
            if (!foundAuto) {
                // Make sure that the variables are loaded for the first data set
                autoLoad = null; // Don't try to load anything else automatically
                datasetSelected( accordion.accordionTabs[0] );
            }
        }
    );
}    

// Called when a new tab has been selected in the left-hand menu
// TODO: Cache the results so we don't have to query the server again when the
// same dataset is selected in future?
// Gets the list of variables for a given dataset from the server and populates
// the correct panel in the left-hand menu
function datasetSelected(expandedTab)
{
    var dataset = expandedTab.titleBar.id;
    // Get the pretty-printed name of the dataset
    prettyDsName = expandedTab.titleBar.firstChild.nodeValue;
    // returns a table of variable names in HTML format
    downloadUrl('wms', 'REQUEST=GetMetadata&item=variables&dataset=' + dataset,
        function(req) {
            var xmldoc = req.responseXML;
            // set the size of the panel to match the number of variables
            var panel = $(dataset + 'Content');
            var varList = xmldoc.getElementsByTagName('tr');
            panel.style.height = varList.length * 20 + 'px';
            panel.innerHTML = req.responseText;
            if (autoLoad != null && autoLoad.variable != null) {
                // TODO: how do we check that this variable exists?
                variableSelected(dataset, autoLoad.variable);
            }
        }
    );
}

// Called when the user clicks on the name of a variable in the left-hand menu
// Gets the details (units, grid etc) of the given variable. 
function variableSelected(datasetName, variableName)
{
    newVariable = true;
    resetAnimation();
    downloadUrl('wms', 'REQUEST=GetMetadata&item=variableDetails&dataset=' + datasetName +
        '&variable=' + variableName,
        function(req) {
            var xmldoc = req.responseXML;
            var varDetails = xmldoc.getElementsByTagName('variableDetails')[0];
            // Set the global variables for dataset and variable name
            var dataset = varDetails.getAttribute('dataset');
            layerName = dataset + '/' + variableName;
            var units = varDetails.getAttribute('units');
            $('datasetName').innerHTML = prettyDsName;
            $('variableName').innerHTML = varDetails.getAttribute('variable');
            $('units').innerHTML = '<b>Units: </b>' + units;
            
            // clear the list of z values
            $('zValues').options.length = 0; 

            // Set the range selector objects
            var theAxes = xmldoc.getElementsByTagName('axis');
            if (autoLoad == null || autoLoad.zValue == null) {
                var zValue = getZValue();
            } else {
                var zValue = parseFloat(autoLoad.zValue);
            }
            for (var i = 0; i < theAxes.length; i++)
            {
                var axisType = theAxes[i].getAttribute('type');
                if (axisType == 'z')
                {
                    zPositive = parseInt(theAxes[i].getAttribute('positive'));
                    var zUnits = theAxes[i].getAttribute('units');
                    if (zPositive) {
                        $('zAxis').innerHTML = '<b>Elevation (' + zUnits + '): </b>';
                    } else {
                        $('zAxis').innerHTML = '<b>Depth (' + zUnits + '): </b>';
                    }
                    // Populate the drop-down list of z values
                    var values = theAxes[i].getElementsByTagName('value');
                    // Make z range selector invisible if there are no z values
                    $('zValues').style.visibility = (values.length == 0) ? 'hidden' : 'visible';
                    var zDiff = 1e10; // Set to some ridiculously-high value
                    var nearestIndex = 0;
                    for (var j = 0; j < values.length; j++) {
                        var optionZValue = values[j].firstChild.nodeValue;
                        // Create an item in the drop-down list for this z level
                        $('zValues').options[j] = new Option(optionZValue, j);
                        // Find the nearest value to the currently-selected
                        // depth level
                        var diff;
                        // This is nasty: improve!
                        if (zPositive) {
                            diff = Math.abs(parseFloat(optionZValue) - zValue);
                        } else {
                            diff = Math.abs(parseFloat(optionZValue) + zValue);
                        }
                        if (diff < zDiff) {
                            zDiff = diff;
                            nearestIndex = j;
                        }
                    }
                    $('zValues').selectedIndex = nearestIndex;
                    var zFound = true;
                }
            }

            if (zFound) {
                $('zValues').style.visibility = 'visible';
            } else {
                $('zAxis').innerHTML = ''
                $('zValues').style.visibility = 'hidden';
            }
            
            $('scaleBar').style.visibility = 'visible';
            $('scaleMin').style.visibility = 'visible';
            $('scaleMax').style.visibility = 'visible';
            $('autoScale').style.visibility = 'visible';
            if (!isIE) {
                // Only show this control if we can use PNGs properly (i.e. not on Internet Explorer)
                $('opacityControl').style.visibility = 'visible';
            }
            
            // Set the auto-zoom box
            bbox = xmldoc.getElementsByTagName('bbox')[0].firstChild.nodeValue;
            $('autoZoom').innerHTML = "<a href=\"#\" onclick=\"javascript:map.zoomToExtent(new OpenLayers.Bounds(" + bbox + "));\">Fit data to window</a>";
            
            // See if we're auto-loading a certain time value
            if (autoLoad != null && autoLoad.tValue != null) {
                tValue = autoLoad.tValue;
            }
            
            // Get the currently-selected time and date or the current time if
            // none has been selected
            if (tValue == null) {
                var now = new Date();
                // Format the date in ISO8601 format
                tValue = now.getFullYear();
                tValue += '-' + (now.getMonth() < 9 ? '0' : '') + (now.getMonth() + 1);
                tValue += '-' + (now.getDate() < 10 ? '0' : '') + now.getDate();
                tValue += 'T00:00:00Z';
            }
            setCalendar(dataset, variableName, tValue);
        }
    );
}

// This requests a calendar for the given date and time for the given dataset
// and variable.  If there is data for the given date and time, this will
// return a calendar for the given month.  If there is no data for the given
// date and time, this will return a calendar for the nearest month.
function setCalendar(dataset, variable, dateTime)
{
    // Set the calendar. When the calendar arrives the map will be updated
    downloadUrl('wms', 'REQUEST=GetMetadata&item=calendar&dataset=' +  dataset + 
        '&variable=' + variable + '&dateTime=' + dateTime,
        function(req) {
            if (req.responseText == '') {
                // There is no calendar data.  Just update the map
                $('calendar').innerHTML = '';
                $('date').innerHTML = '';
                $('time').innerHTML = '';
                $('utc').style.visibility = 'hidden';
                autoScale(); // this also updates the map
                return;
            }
            var xmldoc = req.responseXML;
            $('calendar').innerHTML =
                RicoUtil.getContentAsString(xmldoc.getElementsByTagName('calendar')[0]);
            // If this call has resulted from the selection of a new variable,
            // choose the timestep based on the result from the server
            if (newVariable) {
                var tIndex = parseInt(xmldoc.getElementsByTagName('nearestIndex')[0].firstChild.nodeValue);
                var tVal = xmldoc.getElementsByTagName('nearestValue')[0].firstChild.nodeValue;
                var prettyTVal = xmldoc.getElementsByTagName('prettyNearestValue')[0].firstChild.nodeValue;
                // Get the timesteps for this day and update the map
                getTimesteps(dataset, variable, tIndex, tVal, prettyTVal);
            } else if ($('t' + timestep)) {
                // Highlight the currently-selected timestep if it happens to
                // exist in this calendar
                $('t' + timestep).style.backgroundColor = '#dadee9';
            }
        }
    );
}

// Updates the time selector control.  Finds all the timesteps that occur on
// the same day as the timestep with the given index.   Called from the calendar
// control (see getCalendar.jsp)
function getTimesteps(dataset, variable, tIndex, tVal, prettyTVal)
{
    $('date').innerHTML = '<b>Date/time: </b>' + prettyTVal;
    $('utc').style.visibility = 'visible';
    
    // Get the timesteps
    downloadUrl('wms', 'REQUEST=GetMetadata&item=timesteps&dataset=' +  dataset + 
        '&variable=' + variable + '&tIndex=' + tIndex,
        function(req) {
            $('time').innerHTML = req.responseText; // the data will be a selection box
            if (autoLoad != null && autoLoad.tValue != null) {
                // Now select the relevant item in the selection box
                var timeSelect = $('tValues');
                for (var i = 0; i < timeSelect.options.length; i++) {
                    if (timeSelect.options[i].value == autoLoad.tValue) {
                        timeSelect.selectedIndex = i;
                        break;
                    }
                }
            }
            $('setFrames').style.visibility = 'visible';
            // Make sure the correct day is highlighted in the calendar
            // TODO: doesn't work if there are many timesteps on the same day!
            if ($('t' + timestep)) {
                $('t' + timestep).style.backgroundColor = 'white';
            }
            timestep = tIndex;
            if ($('t' + timestep)) {
                $('t' + timestep).style.backgroundColor = '#dadee9';
            }
            if (autoLoad != null && autoLoad.scaleMin != null && autoLoad.scaleMax != null) {
                $('scaleMin').value = autoLoad.scaleMin;
                $('scaleMax').value = autoLoad.scaleMax;
                validateScale(); // this calls updateMap()
            } else if (newVariable) {
                autoScale(); // Scales the map automatically and updates it
            } else {
                updateMap(); // Update the map without changing the scale
            }
        }
    );
}

// Calls the WMS to find the min and max data values, then rescales.
// If this is a newly-selected variable the method gets the min and max values
// for the whole layer.  If not, this gets the min and max values for the viewport.
function autoScale()
{
    var dataBounds = bbox;
    if ($('tValues')) {
        tValue = $('tValues').value;
    }
    if (newVariable) {
        newVariable = false; // This will be set true when we click on a different variable name
    } else {
        // Use the intersection of the viewport and the layer's bounding box
        dataBounds = getIntersectionBBOX();
    }
    // Get the minmax metadata item.  This gets a grid of 50x50 data points
    // covering the BBOX and finds the min and max values
    downloadUrl('wms', 'REQUEST=GetMetadata&item=minmax&layers=' +
        layerName + '&BBOX=' + dataBounds + '&WIDTH=50&HEIGHT=50'
        + '&CRS=CRS:84&ELEVATION=' + getZValue() + '&TIME=' + tValue,
        function(req) {
            var xmldoc = req.responseXML;
            // set the size of the panel to match the number of variables
            $('scaleMin').value = xmldoc.getElementsByTagName('min')[0].firstChild.nodeValue;
            $('scaleMax').value = xmldoc.getElementsByTagName('max')[0].firstChild.nodeValue;
            validateScale(); // This calls updateMap()
        }
    );
}

// Validates the entries for the scale bar
function validateScale()
{
    var fMin = parseFloat($('scaleMin').value);
    var fMax = parseFloat($('scaleMax').value);
    if (isNaN(fMin))
    {
        alert('Scale limits must be set to valid numbers');
        // Reset to the old value
        $('scaleMin').value = scaleMinVal;
    }
    else if (isNaN(fMax))
    {
        alert('Scale limits must be set to valid numbers');
        // Reset to the old value
        $('scaleMax').value = scaleMaxVal;
    }
    else if (fMin >= fMax)
    {
        alert('Minimum scale value must be less than the maximum');
        // Reset to the old values
        $('scaleMin').value = scaleMinVal;
        $('scaleMax').value = scaleMaxVal;
    }
    else   
    {
        $('scaleMin').value = fMin;
        $('scaleMax').value = fMax;
        scaleMinVal = fMin;
        scaleMaxVal = fMax;
        updateMap();
    }
}

function resetAnimation()
{
    hideAnimation();
    $('setFrames').style.visibility = 'hidden';
    $('animation').style.visibility = 'hidden';
    $('firstFrame').innerHTML = '';
    $('lastFrame').innerHTML = '';
}
function setFirstAnimationFrame()
{
    $('firstFrame').innerHTML = $('tValues').value;
    $('animation').style.visibility = 'visible';
    setGEarthURL();
}
function setLastAnimationFrame()
{
    $('lastFrame').innerHTML = $('tValues').value;
    $('animation').style.visibility = 'visible';
    setGEarthURL();
}
function createAnimation()
{
    if (!timeSeriesSelected())
    {
        alert("Must select a first and last frame for the animation");
        return;
    }
    
    //essc_wms.mergeNewParams({time: $('firstFrame').innerHTML + '/' + $('lastFrame').innerHTML,
    //    format: 'image/gif'});
    //return;
    
    // Get a URL for a WMS request that covers the current map extent
    var urlEls = essc_wms.getURL(map.getExtent()).split('&');
    // Replace the parameters as needed.  We generate a map that is half the
    // width and height of the viewport, otherwise it takes too long
    var newURL = urlEls[0];
    for (var i = 1; i < urlEls.length; i++) {
        if (urlEls[i].startsWith('TIME=')) {
            newURL += '&TIME=' + $('firstFrame').innerHTML + '/' + $('lastFrame').innerHTML;
        } else if (urlEls[i].startsWith('FORMAT')) {
            newURL += '&FORMAT=image/gif';
        } else if (urlEls[i].startsWith('WIDTH')) {
            newURL += '&WIDTH=' + $('map').clientWidth / 2;
        } else if (urlEls[i].startsWith('HEIGHT')) {
            newURL += '&HEIGHT=' + $('map').clientHeight / 2;
        } else if (!urlEls[i].startsWith('OPACITY')) {
            // We remove the OPACITY ARGUMENT as GIFs do not support partial transparency
            newURL += '&' + urlEls[i];
        }
    }
    $('featureInfo').style.visibility = 'hidden';
    $('autoZoom').style.visibility = 'hidden';
    $('hideAnimation').style.visibility = 'visible';
    // We show the "please wait" image then immediately load the animation
    $('loadingAnimationDiv').style.visibility = 'visible'; // This will be hidden by animationLoaded()
    $('mapOverlay').src = newURL;
    $('mapOverlay').width = $('map').clientWidth;
    $('mapOverlay').height = $('map').clientHeight;
}
function animationLoaded()
{
    $('loadingAnimationDiv').style.visibility = 'hidden';
    $('mapOverlayDiv').style.visibility = 'visible';
    if (essc_wms != null) {
        essc_wms.setVisibility(false);
    }
}
function hideAnimation()
{
    if (essc_wms != null) {
        essc_wms.setVisibility(true);
    }
    $('featureInfo').style.visibility = 'visible';
    $('autoZoom').style.visibility = 'visible';
    $('hideAnimation').style.visibility = 'hidden';
    $('mapOverlayDiv').style.visibility = 'hidden';
}

function updateMap()
{    
    // Update the intermediate scale markers
    var scaleOneThird = parseFloat(scaleMinVal) + ((scaleMaxVal - scaleMinVal) / 3);
    var scaleTwoThirds = parseFloat(scaleMinVal) + (2 * (scaleMaxVal - scaleMinVal) / 3);
    $('scaleOneThird').innerHTML = toNSigFigs(scaleOneThird, 4);
    $('scaleTwoThirds').innerHTML = toNSigFigs(scaleTwoThirds, 4);
    
    if ($('tValues')) {
        tValue = $('tValues').value;
    }
    
    var opacity = $('opacityValue').value;
    
    // Set the map bounds automatically
    if (autoLoad != null && autoLoad.bbox != null) {
        map.zoomToExtent(getBounds(autoLoad.bbox));
    }
    
    // Make sure the autoLoad object is cleared
    autoLoad = null;

    // Notify the OpenLayers widget
    // SCALE=minval,maxval is a non-standard extension to WMS, describing how
    // the map is to be coloured.
    // OPACITY=[0,100] is another non-standard extension to WMS, giving the opacity
    // of the data pixels
    // TODO get the map projection from the base layer
    // TODO use a more informative title
    // Buffer is set to 1 to avoid loading a large halo of tiles outside the
    // current viewport
    var baseURL = window.location.href.split("/").slice(0,-1).join("/");
    if (essc_wms == null) {
        // If this were an Untiled layer we could control the ratio of image
        // size to viewport size with "{buffer: 1, ratio: 1.5}"
        essc_wms = new OpenLayers.Layer.WMS1_3("ESSC WMS",
            baseURL + '/wms', {
            layers: layerName,
            elevation: getZValue(),
            time: tValue,
            transparent: 'true',
            // Temporarily commented out to allow default style to be used.
            // TODO: provide option to choose STYLE on web interface.
            styles: 'boxfill;scale:' + scaleMinVal + ':' + scaleMaxVal + ';opacity:' + opacity},
            {buffer: 1, ratio: 1.5}
        );
        map.addLayers([essc_wms]);
    } else {
        essc_wms.mergeNewParams({
            layers: layerName,
            elevation: getZValue(),
            time: tValue,
            styles: 'boxfill;scale:' + scaleMinVal + ':' + scaleMaxVal + ';opacity:' + opacity}
        );
    }
    
    $('featureInfo').innerHTML = "Click on the map to get more information";
    $('featureInfo').style.visibility = 'visible';
    
    var imageURL = essc_wms.getURL(getBounds(bbox));
    $('testImage').innerHTML = '<a href=\'' + imageURL + '\'>link to test image</a>';
    setGEarthURL();
    setPermalinkURL();
}

// Gets the Z value set by the user
function getZValue()
{
    // If we have no depth information, assume we're at the surface.  This
    // will be ignored by the map server
    var zIndex = $('zValues').selectedIndex;
    var zValue = $('zValues').options.length == 0 ? 0 : $('zValues').options[zIndex].firstChild.nodeValue;
    return zPositive ? zValue : -zValue;
}

// Sets the permalink
function setPermalinkURL()
{
    if (layerName != '') {
        var url = window.location.protocol + '//' +
            window.location.host +
            window.location.pathname +
            '?dataset=' + layerName.split('/')[0] +
            '&variable=' + layerName.split('/')[1] +
            '&elevation=' + getZValue() +
            '&time=' + tValue +
            '&scale=' + scaleMinVal + ',' + scaleMaxVal +
            '&bbox=' + map.getExtent().toBBOX();
        $('permalink').innerHTML = '<a target="_blank" href="' + url +
            '">Permalink</a>&nbsp;|&nbsp;<a href="mailto:?subject=Godiva2%20link&body='
            + escape(url) + '">email</a>';
        $('permalink').style.visibility = 'visible';
    }
}

// Sets the URL for "Open in Google Earth" and the permalink
function setGEarthURL()
{
    if (essc_wms != null) {
        // Get a URL for a WMS request that covers the current map extent
        var mapBounds = map.getExtent();
        var urlEls = essc_wms.getURL(mapBounds).split('&');
        var gEarthURL = urlEls[0];
        for (var i = 1; i < urlEls.length; i++) {
            if (urlEls[i].startsWith('FORMAT')) {
                // Make sure the FORMAT is set correctly
                gEarthURL += '&FORMAT=application/vnd.google-earth.kmz';
            } else if (urlEls[i].startsWith('TIME') && timeSeriesSelected()) {
                // If we can make an animation, do so
                gEarthURL += '&TIME=' + $('firstFrame').innerHTML + '/' + $('lastFrame').innerHTML;
            } else if (urlEls[i].startsWith('BBOX')) {
                // Set the bounding box so that there are no transparent pixels around
                // the edge of the image: i.e. find the intersection of the layer BBOX
                // and the viewport BBOX
                gEarthURL += '&BBOX=' + getIntersectionBBOX();
            } else if (!urlEls[i].startsWith('OPACITY')) {
                // We remove the OPACITY argument as Google Earth allows opacity
                // to be controlled in the client
                gEarthURL += '&' + urlEls[i];
            }
        }
        if (timeSeriesSelected()) {
            $('googleEarth').innerHTML = '<a href=\'' + gEarthURL + '\'>Open animation in Google Earth</a>';
        } else {
            $('googleEarth').innerHTML = '<a href=\'' + gEarthURL + '\'>Open in Google Earth</a>';
        }
    }
}

// Returns a bounding box as a string in format "minlon,minlat,maxlon,maxlat"
// that represents the intersection of the currently-visible map layer's 
// bounding box and the viewport's bounding box.
function getIntersectionBBOX()
{
    var mapBounds = map.getExtent();
    var mapBboxEls = mapBounds.toBBOX().split(',');
    // bbox is the bounding box of the currently-visible layer
    var layerBboxEls = bbox.split(',');
    var newBBOX = Math.max(parseFloat(mapBboxEls[0]), parseFloat(layerBboxEls[0])) + ',';
    newBBOX += Math.max(parseFloat(mapBboxEls[1]), parseFloat(layerBboxEls[1])) + ',';
    newBBOX += Math.min(parseFloat(mapBboxEls[2]), parseFloat(layerBboxEls[2])) + ',';
    newBBOX += Math.min(parseFloat(mapBboxEls[3]), parseFloat(layerBboxEls[3]));
    return newBBOX;
}

// Formats the given value to numSigFigs significant figures
function toNSigFigs(value, numSigFigs)
{
    var strValue = '' + value;
    var newValue = '';
    var firstSigFigPos = -1;
    var dpSeen = 0; // Will be 1 when we have seen the decimal point

    for (var i = 0; i < strValue.length; i++)
    {
        if (firstSigFigPos < 0)
        {
            // Haven't found the first significant figure yet
            newValue += strValue.charAt(i);
            if (strValue.charAt(i) != '0' && strValue.charAt(i) != '.'
                && strValue.charAt(i) != '-')
            {
                // We've found the first significant figure
                firstSigFigPos = i;
            }
        }
        else
        {
            // We don't want to count the decimal point as a sig fig!
            if (strValue.charAt(i) == '.')
            {
                dpSeen = 1;
            }
            if (i - firstSigFigPos < numSigFigs + dpSeen)
            {
                newValue += strValue.charAt(i);
            }
        }
    }
    return newValue;
}

// Returns true if the user has selected a time series
function timeSeriesSelected()
{
    return $('firstFrame').innerHTML != '' && $('lastFrame').innerHTML != '';
}

// Takes a BBOX string of the form "minlon,minlat,maxlon,maxlat" and returns
// the corresponding OpenLayers.Bounds object
// TODO: error checking
function getBounds(bboxStr)
{
    var bboxEls = bboxStr.split(",");
    return new OpenLayers.Bounds(parseFloat(bboxEls[0]), parseFloat(bboxEls[1]),
        parseFloat(bboxEls[2]), parseFloat(bboxEls[3]));
}