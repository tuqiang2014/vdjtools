/*
 * Copyright (c) 2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */

package com.antigenomics.vdjtools.profile

import com.antigenomics.vdjtools.sample.Sample
import com.antigenomics.vdjtools.sample.SampleCollection

import static com.antigenomics.vdjtools.util.ExecUtil.formOutputPath

def DEFAULT_MIN_COUNT = "1", DEFAULT_MIN_FREQ = "0.001"
def cli = new CliBuilder(usage: "ComputePwms [options] " +
        "[sample1 sample2 sample3 ... if -m is not specified] output_prefix")
cli.h("display help message")
cli.m(longOpt: "metadata", argName: "filename", args: 1,
        "Metadata file. First and second columns should contain file name and sample id. " +
                "Header is mandatory and will be used to assign column names for metadata.")
cli._(longOpt: "raw", "Will output raw frequencies, so the matrix could be loaded and further re-used")
cli._(longOpt: "correct", "Will correct PWMs having a small number of underlying clonotypes")
cli.n(longOpt: "normalize", "Will normalize PWMs with respect to a pre-built control " +
        "(n=70+ healthy subjects of various sexes and ages)")
cli._(longOpt: "min-count", argName: "integer", args: 1,
        "Minimal number of clonotypes in a PWM grid cell for it to be reported. " +
                "[default = $DEFAULT_MIN_COUNT]")
cli._(longOpt: "min-freq", argName: "double", args: 1,
        "Minimal overall frequency of clonotypes in a PWM grid cell for it to be reported. " +
                "[default = $DEFAULT_MIN_FREQ]")
cli.p(longOpt: "plot", "Plot matrices with PWMs")
cli.f(longOpt: "factor", argName: "string", args: 1, "Metadata entry used to split samples. " +
        "Factor values will be interpreted as a discrete set.")

def opt = cli.parse(args)

if (opt == null)
    System.exit(-1)

if (opt.h || opt.arguments().size() == 0) {
    cli.usage()
    System.exit(-1)
}

// Check if metadata is provided

def metadataFileName = opt.m

if (metadataFileName ? opt.arguments().size() != 1 : opt.arguments().size() < 2) {
    if (metadataFileName)
        println "Only output prefix should be provided in case of -m"
    else
        println "At least 1 sample files should be provided if not using -m"
    cli.usage()
    System.exit(-1)
}

def outputPrefix = opt.arguments()[-1],
    factor = (String) (opt.f ?: null),
    minCount = (opt.'min-count' ?: DEFAULT_MIN_COUNT).toInteger(),
    minFreq = (opt.'min-freq' ?: DEFAULT_MIN_FREQ).toDouble(),
    correct = (boolean) opt.'correct',
    raw = (boolean) opt.'raw',
    normalize = (boolean) opt.n,
    plot = (boolean) opt.p

def scriptName = getClass().canonicalName.split("\\.")[-1]

//
// Batch load all samples (lazy)
//

println "[${new Date()} $scriptName] Reading samples"

def sampleCollection = metadataFileName ?
        new SampleCollection((String) metadataFileName) :
        new SampleCollection(opt.arguments()[0..-2])
def metadataTable = sampleCollection.metadataTable

println "[${new Date()} $scriptName] ${sampleCollection.size()} sample(s) prepared"

//
// Check factor exists
//

if (factor) {
    def factorCol = metadataTable.getColumn(factor)
    if (!factorCol) {
        println "[ERROR] Factor $factor does not exist in metadata, possible factors:\n" +
                "${metadataTable.columnHeader}"
        System.exit(-1)
    }
}

//
// Split samples by factor and group within CdrPwmGrids
//

def pwmGridMap = new HashMap<String, CdrPwmGrid>()
sampleCollection.each { Sample sample ->
    def factorName = factor ? (String) sample.sampleMetadata[factor] : "all"

    def pwmGrid = pwmGridMap[factorName]
    if (!pwmGrid)
        pwmGridMap.put(factorName, pwmGrid = new CdrPwmGrid())

    pwmGrid.update(sample)

    println "[${new Date()} $scriptName] Updated PWM grid for subset '$factorName'"
}

//
// Report those pwms
//

println "[${new Date()} $scriptName] Writing PWM grid(s)"

pwmGridMap.each {
    new File(formOutputPath(outputPrefix, "pwmgrid", it.key)).withPrintWriter { pw ->
        pw.println(CdrPwmGrid.HEADER)
        pw.println(raw ? it.value.toStringRaw() :
                it.value.toString(minCount, minFreq, normalize, correct))
    }
}

println "[${new Date()} $scriptName] Finished"