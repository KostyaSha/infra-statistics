import java.util.zip.GZIPInputStream;

import groovy.xml.MarkupBuilder


def workingDir = new File("target")
def svgDir = new File(workingDir, "svg")


def generateStats(file, targetDir) {

    JenkinsMetricParser p = new JenkinsMetricParser()
    def installations = p.parse(file)

    def version2number = [:]
    def plugin2number = [:]
    def jobtype2number = [:]
    def nodesOnOs2number = [:]

    installations.each { instId, metric ->

        //        println instId +"="+metric.jenkinsVersion
        def currentNumber = version2number.get(metric.jenkinsVersion)
        def number = currentNumber ? currentNumber + 1 : 1
        version2number.put(metric.jenkinsVersion, number)

        metric.plugins.each { pluginName, pluginVersion ->
            def currentPluginNumber = plugin2number.get(pluginName)
            currentPluginNumber = currentPluginNumber ? currentPluginNumber + 1 : 1
            plugin2number.put(pluginName, currentPluginNumber)
        }

        metric.jobTypes.each { jobtype, jobNumber ->
            def currentJobNumber = jobtype2number.get(jobtype)
            currentJobNumber = currentJobNumber ? currentJobNumber + jobNumber : jobNumber
            jobtype2number.put(jobtype, currentJobNumber)
        }

        metric.nodesOnOs.each { os, nodesNumber ->
            def currentNodeNumber = nodesOnOs2number.get(os)
            currentNodeNumber = currentNodeNumber ? currentNodeNumber + nodesNumber : nodesNumber
            nodesOnOs2number.put(os, currentNodeNumber)
        }

    }

    def nodesOs = []
    def nodesOsNrs = []
    nodesOnOs2number.each{os, number ->
        nodesOs.add(os)
        nodesOsNrs.add(number)
    }

    def simplename = file.name.substring(0, file.name.lastIndexOf("."))



    createBarSVG(new File(targetDir, "$simplename-jenkins.svg"), version2number, 10, false, {it.value >= 5})
    createBarSVG(new File(targetDir, "$simplename-plugins.svg"), plugin2number, 15, true, {!it.key.startsWith("privateplugin")})
    createBarSVG(new File(targetDir, "$simplename-jobs.svg"), jobtype2number, 1000, true, {!it.key.startsWith("private")})
    createBarSVG(new File(targetDir, "$simplename-nodes.svg"), nodesOnOs2number, 10, true, {true})
    createPieSVG(new File(targetDir, "$simplename-nodesPie.svg"), nodesOsNrs, 200, 200, 150, Helper.COLORS, nodesOs, 370, 20)

}



def createBarSVG(def svgFile, def item2number, def scaleReduction, boolean sortByValue, Closure filter){

    svgFile.delete()

    def higestNr = item2number.inject(0){ input, version, number -> number > input ? number : input }

    // ignore all private types
    item2number = item2number.findAll(filter) //{!it.key.startsWith("private")}

    if(sortByValue) {
        item2number = item2number.sort{ a, b -> a.value <=> b.value }
    }else{
        item2number = item2number.sort({ k1, k2 -> k1 <=> k2} as Comparator)
    }


    def viewWidth = (item2number.size() * 15) + 30

    def pwriter = new FileWriter(svgFile)
    def pxml = new MarkupBuilder(pwriter)
    pxml.svg('xmlns': 'http://www.w3.org/2000/svg', "version": "1.1", "preserveAspectRatio":'none', "viewBox": "0 0 "+ viewWidth +" "+((higestNr / scaleReduction)+200)) {
        // 200 for the text/legend

        def total = 0

        item2number.eachWithIndex { item, number, index ->

            total += number

            def barHeight = number / scaleReduction

            def x = (index + 1) * 15
            def y = (higestNr / scaleReduction ) - barHeight
            rect(fill:"blue", height: barHeight, stroke:"black", width:"12", x:x, y:y) {
            }
            def ty = y + barHeight + 5
            def tx = x
            text(x:tx, y:ty, "font-family":'Tahoma', "font-size":'12', transform:"rotate(90 $tx,$ty)", "text-rendering":'optimizeSpeed', fill:'#000000;', "$item ($number)"){}
        }

        text(x:'10', y:'100', "font-family":'Tahoma', "font-size":'20', "text-rendering":'optimizeSpeed', fill:'#000000;', "Total: ${total}"){}
    }
}


/**
 * www.davidflanagan.com/javascript5/display.php?n=22-8&f=22/08.js
 *
 * Draw a pie chart into an <svg> element.
 * Arguments:
 *   canvas: the SVG element (or the id of that element) to draw into.
 *   data: an array of numbers to chart, one for each wedge of the pie.
 *   cx, cy, r: the center and radius of the pie
 *   colors: an array of HTML color strings, one for each wedge
 *   labels: an array of labels to appear in the legend, one for each wedge
 *   lx, ly: the upper-left corner of the chart legend
 */
def createPieSVG(def svgFile, def data,def cx,def cy,def r,def colors,def labels,def lx,def ly) {

    // Add up the data values so we know how big the pie is
    def total = 0;
    for(def i = 0; i < data.size(); i++) total += data[i];

    // Now figure out how big each slice of pie is.  Angles in radians.
    def angles = []
    for(def i = 0; i < data.size(); i++) angles[i] = data[i]/total*Math.PI*2;

    // Loop through each slice of pie.
    def startangle = 0;

    def pwriter = new FileWriter(svgFile)
    def pxml = new MarkupBuilder(pwriter)
    pxml.svg('xmlns': 'http://www.w3.org/2000/svg', "version": "1.1", "preserveAspectRatio":'none') {

        data.eachWithIndex { item, i ->
            // This is where the wedge ends
            def endangle = startangle + angles[i];

            // Compute the two points where our wedge intersects the circle
            // These formulas are chosen so that an angle of 0 is at 12 o'clock
            // and positive angles increase clockwise.
            def x1 = cx + r * Math.sin(startangle);
            def y1 = cy - r * Math.cos(startangle);
            def x2 = cx + r * Math.sin(endangle);
            def y2 = cy - r * Math.cos(endangle);

            // This is a flag for angles larger than than a half circle
            def big = 0;
            if (endangle - startangle > Math.PI) {big = 1}

            // We describe a wedge with an <svg:path> element
            // Notice that we create this with createElementNS()
            //            def path = document.createElementNS(SVG.ns, "path");

            // This string holds the path details
            def d = "M " + cx + "," + cy +      // Start at circle center
                    " L " + x1 + "," + y1 +     // Draw line to (x1,y1)
                    " A " + r + "," + r +       // Draw an arc of radius r
                    " 0 " + big + " 1 " +       // Arc details...
                    x2 + "," + y2 +             // Arc goes to to (x2,y2)
                    " Z";                       // Close path back to (cx,cy)

            path(   d: d, // Set this path
                    fill: colors[i], // Set wedge color
                    stroke: "black", // Outline wedge in black
                    "stroke-width": "1" // 1 unit thick
                    ){}

            // The next wedge begins where this one ends
            startangle = endangle;

            // Now draw a little matching square for the key
            rect(   x: lx,  // Position the square
                    y: ly + 30*i,
                    "width": 20, // Size the square
                    "height": 20,
                    "fill": colors[i], // Same fill color as wedge
                    "stroke": "black", // Same outline, too.
                    "stroke-width": "1"){}

            // And add a label to the right of the rectangle
            text(   "x": lx + 30, // Position the text
                    "y": ly + 30*i + 18,
                    "font-family": "sans-serif",
                    "font-size": "16",
                    "${labels[i]} ($item)"){}
        }
    }
}

def createHtml(dir) {
    def pwriter = new FileWriter(new File(dir, "svgs.html"))
    def phtml = new MarkupBuilder(pwriter)
    phtml.html() {
        body(){
            ul(){
                dir.eachFileMatch( ~".*svg" ) { file ->
                    li(){
                        a(href: file.name, file.name)
                    }
                }
            }
        }
    }
}

def run = {
    svgDir.deleteDir()
    svgDir.mkdirs()
    workingDir.eachFileMatch( ~".*json" ) { file -> generateStats(file, svgDir) }
    //    workingDir.eachFileMatch( ~"201109.json" ) { file -> generateStats(file, svgDir) }
    createHtml(svgDir)
}

run()
