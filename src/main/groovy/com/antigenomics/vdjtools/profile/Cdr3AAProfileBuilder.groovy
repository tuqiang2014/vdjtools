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

import com.antigenomics.vdjtools.sample.Clonotype

class Cdr3AAProfileBuilder {
    private final Map<Cdr3Region, Integer> binning
    private final boolean weighted, excludeCysPhe
    private final List<String> groups

    Cdr3AAProfileBuilder(Map<Cdr3Region, Integer> binning, boolean weighted,
                         boolean excludeCysPhe,
                         String... groups) {
        this.binning = binning
        this.weighted = weighted
        this.excludeCysPhe = excludeCysPhe
        this.groups = groups
    }

    Map<Cdr3Region, AminoAcidProfile> create(Iterable<Clonotype> clonotypes) {
        def profiles = new HashMap<Cdr3Region, AminoAcidProfile>()

        binning.each {
            profiles.put(it.key, new AminoAcidProfile(it.value,
                    BasicAminoAcidProperties.INSTANCE.getProperties(groups)))
        }

        clonotypes.each { clonotype ->
            if (clonotype.isCoding()) {
                profiles.each {
                    def aaSeq = it.key.extractAminoAcid(clonotype, excludeCysPhe)
                    if (aaSeq.size() > 0) {
                        it.value.update(aaSeq, weighted ? clonotype.freq : 1.0d)
                    }
                }
            }
        }

        profiles
    }
}
