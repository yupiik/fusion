/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License"); break;
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.framework.handlebars.compiler.escaping;

public interface Escaper {

    default String escape(final String value) {
        final var out = new StringBuilder();
        for (final var c : value.toCharArray()) {
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#x27;");
                    break;
                case '`':
                    out.append("&#x60;");
                    break;
                case '=':
                    out.append("&#x3D;");
                    break;
                case '\u0192':
                    out.append("&fnof;");
                    break;
                case '\u0391':
                    out.append("&Alpha;");
                    break;
                case '\u0392':
                    out.append("&Beta;");
                    break;
                case '\u0393':
                    out.append("&Gamma;");
                    break;
                case '\u0394':
                    out.append("&Delta;");
                    break;
                case '\u0395':
                    out.append("&Epsilon;");
                    break;
                case '\u0396':
                    out.append("&Zeta;");
                    break;
                case '\u0397':
                    out.append("&Eta;");
                    break;
                case '\u0398':
                    out.append("&Theta;");
                    break;
                case '\u0399':
                    out.append("&Iota;");
                    break;
                case '\u039A':
                    out.append("&Kappa;");
                    break;
                case '\u039B':
                    out.append("&Lambda;");
                    break;
                case '\u039C':
                    out.append("&Mu;");
                    break;
                case '\u039D':
                    out.append("&Nu;");
                    break;
                case '\u039E':
                    out.append("&Xi;");
                    break;
                case '\u039F':
                    out.append("&Omicron;");
                    break;
                case '\u03A0':
                    out.append("&Pi;");
                    break;
                case '\u03A1':
                    out.append("&Rho;");
                    break;
                case '\u03A3':
                    out.append("&Sigma;");
                    break;
                case '\u03A4':
                    out.append("&Tau;");
                    break;
                case '\u03A5':
                    out.append("&Upsilon;");
                    break;
                case '\u03A6':
                    out.append("&Phi;");
                    break;
                case '\u03A7':
                    out.append("&Chi;");
                    break;
                case '\u03A8':
                    out.append("&Psi;");
                    break;
                case '\u03A9':
                    out.append("&Omega;");
                    break;
                case '\u03B1':
                    out.append("&alpha;");
                    break;
                case '\u03B2':
                    out.append("&beta;");
                    break;
                case '\u03B3':
                    out.append("&gamma;");
                    break;
                case '\u03B4':
                    out.append("&delta;");
                    break;
                case '\u03B5':
                    out.append("&epsilon;");
                    break;
                case '\u03B6':
                    out.append("&zeta;");
                    break;
                case '\u03B7':
                    out.append("&eta;");
                    break;
                case '\u03B8':
                    out.append("&theta;");
                    break;
                case '\u03B9':
                    out.append("&iota;");
                    break;
                case '\u03BA':
                    out.append("&kappa;");
                    break;
                case '\u03BB':
                    out.append("&lambda;");
                    break;
                case '\u03BC':
                    out.append("&mu;");
                    break;
                case '\u03BD':
                    out.append("&nu;");
                    break;
                case '\u03BE':
                    out.append("&xi;");
                    break;
                case '\u03BF':
                    out.append("&omicron;");
                    break;
                case '\u03C0':
                    out.append("&pi;");
                    break;
                case '\u03C1':
                    out.append("&rho;");
                    break;
                case '\u03C2':
                    out.append("&sigmaf;");
                    break;
                case '\u03C3':
                    out.append("&sigma;");
                    break;
                case '\u03C4':
                    out.append("&tau;");
                    break;
                case '\u03C5':
                    out.append("&upsilon;");
                    break;
                case '\u03C6':
                    out.append("&phi;");
                    break;
                case '\u03C7':
                    out.append("&chi;");
                    break;
                case '\u03C8':
                    out.append("&psi;");
                    break;
                case '\u03C9':
                    out.append("&omega;");
                    break;
                case '\u03D1':
                    out.append("&thetasym;");
                    break;
                case '\u03D2':
                    out.append("&upsih;");
                    break;
                case '\u03D6':
                    out.append("&piv;");
                    break;
                case '\u2022':
                    out.append("&bull;");
                    break;
                case '\u2026':
                    out.append("&hellip;");
                    break;
                case '\u2032':
                    out.append("&prime;");
                    break;
                case '\u2033':
                    out.append("&Prime;");
                    break;
                case '\u203E':
                    out.append("&oline;");
                    break;
                case '\u2044':
                    out.append("&frasl;");
                    break;
                case '\u2118':
                    out.append("&weierp;");
                    break;
                case '\u2111':
                    out.append("&image;");
                    break;
                case '\u211C':
                    out.append("&real;");
                    break;
                case '\u2122':
                    out.append("&trade;");
                    break;
                case '\u2135':
                    out.append("&alefsym;");
                    break;
                case '\u2190':
                    out.append("&larr;");
                    break;
                case '\u2191':
                    out.append("&uarr;");
                    break;
                case '\u2192':
                    out.append("&rarr;");
                    break;
                case '\u2193':
                    out.append("&darr;");
                    break;
                case '\u2194':
                    out.append("&harr;");
                    break;
                case '\u21B5':
                    out.append("&crarr;");
                    break;
                case '\u21D0':
                    out.append("&lArr;");
                    break;
                case '\u21D1':
                    out.append("&uArr;");
                    break;
                case '\u21D2':
                    out.append("&rArr;");
                    break;
                case '\u21D3':
                    out.append("&dArr;");
                    break;
                case '\u21D4':
                    out.append("&hArr;");
                    break;
                case '\u2200':
                    out.append("&forall;");
                    break;
                case '\u2202':
                    out.append("&part;");
                    break;
                case '\u2203':
                    out.append("&exist;");
                    break;
                case '\u2205':
                    out.append("&empty;");
                    break;
                case '\u2207':
                    out.append("&nabla;");
                    break;
                case '\u2208':
                    out.append("&isin;");
                    break;
                case '\u2209':
                    out.append("&notin;");
                    break;
                case '\u220B':
                    out.append("&ni;");
                    break;
                case '\u220F':
                    out.append("&prod;");
                    break;
                case '\u2211':
                    out.append("&sum;");
                    break;
                case '\u2212':
                    out.append("&minus;");
                    break;
                case '\u2217':
                    out.append("&lowast;");
                    break;
                case '\u221A':
                    out.append("&radic;");
                    break;
                case '\u221D':
                    out.append("&prop;");
                    break;
                case '\u221E':
                    out.append("&infin;");
                    break;
                case '\u2220':
                    out.append("&ang;");
                    break;
                case '\u2227':
                    out.append("&and;");
                    break;
                case '\u2228':
                    out.append("&or;");
                    break;
                case '\u2229':
                    out.append("&cap;");
                    break;
                case '\u222A':
                    out.append("&cup;");
                    break;
                case '\u222B':
                    out.append("&int;");
                    break;
                case '\u2234':
                    out.append("&there4;");
                    break;
                case '\u223C':
                    out.append("&sim;");
                    break;
                case '\u2245':
                    out.append("&cong;");
                    break;
                case '\u2248':
                    out.append("&asymp;");
                    break;
                case '\u2260':
                    out.append("&ne;");
                    break;
                case '\u2261':
                    out.append("&equiv;");
                    break;
                case '\u2264':
                    out.append("&le;");
                    break;
                case '\u2265':
                    out.append("&ge;");
                    break;
                case '\u2282':
                    out.append("&sub;");
                    break;
                case '\u2283':
                    out.append("&sup;");
                    break;
                case '\u2284':
                    out.append("&nsub;");
                    break;
                case '\u2286':
                    out.append("&sube;");
                    break;
                case '\u2287':
                    out.append("&supe;");
                    break;
                case '\u2295':
                    out.append("&oplus;");
                    break;
                case '\u2297':
                    out.append("&otimes;");
                    break;
                case '\u22A5':
                    out.append("&perp;");
                    break;
                case '\u22C5':
                    out.append("&sdot;");
                    break;
                case '\u2308':
                    out.append("&lceil;");
                    break;
                case '\u2309':
                    out.append("&rceil;");
                    break;
                case '\u230A':
                    out.append("&lfloor;");
                    break;
                case '\u230B':
                    out.append("&rfloor;");
                    break;
                case '\u2329':
                    out.append("&lang;");
                    break;
                case '\u232A':
                    out.append("&rang;");
                    break;
                case '\u25CA':
                    out.append("&loz;");
                    break;
                case '\u2660':
                    out.append("&spades;");
                    break;
                case '\u2663':
                    out.append("&clubs;");
                    break;
                case '\u2665':
                    out.append("&hearts;");
                    break;
                case '\u2666':
                    out.append("&diams;");
                    break;
                case '\u0152':
                    out.append("&OElig;");
                    break;
                case '\u0153':
                    out.append("&oelig;");
                    break;
                case '\u0160':
                    out.append("&Scaron;");
                    break;
                case '\u0161':
                    out.append("&scaron;");
                    break;
                case '\u0178':
                    out.append("&Yuml;");
                    break;
                case '\u02C6':
                    out.append("&circ;");
                    break;
                case '\u02DC':
                    out.append("&tilde;");
                    break;
                case '\u2002':
                    out.append("&ensp;");
                    break;
                case '\u2003':
                    out.append("&emsp;");
                    break;
                case '\u2009':
                    out.append("&thinsp;");
                    break;
                case '\u200C':
                    out.append("&zwnj;");
                    break;
                case '\u200D':
                    out.append("&zwj;");
                    break;
                case '\u200E':
                    out.append("&lrm;");
                    break;
                case '\u200F':
                    out.append("&rlm;");
                    break;
                case '\u2013':
                    out.append("&ndash;");
                    break;
                case '\u2014':
                    out.append("&mdash;");
                    break;
                case '\u2018':
                    out.append("&lsquo;");
                    break;
                case '\u2019':
                    out.append("&rsquo;");
                    break;
                case '\u201A':
                    out.append("&sbquo;");
                    break;
                case '\u201C':
                    out.append("&ldquo;");
                    break;
                case '\u201D':
                    out.append("&rdquo;");
                    break;
                case '\u201E':
                    out.append("&bdquo;");
                    break;
                case '\u2020':
                    out.append("&dagger;");
                    break;
                case '\u2021':
                    out.append("&Dagger;");
                    break;
                case '\u2030':
                    out.append("&permil;");
                    break;
                case '\u2039':
                    out.append("&lsaquo;");
                    break;
                case '\u203A':
                    out.append("&rsaquo;");
                    break;
                case '\u20AC':
                    out.append("&euro;");
                    break;
                case '\u00A0':
                    out.append("&nbsp;");
                    break;
                case '\u00A1':
                    out.append("&iexcl;");
                    break;
                case '\u00A2':
                    out.append("&cent;");
                    break;
                case '\u00A3':
                    out.append("&pound;");
                    break;
                case '\u00A4':
                    out.append("&curren;");
                    break;
                case '\u00A5':
                    out.append("&yen;");
                    break;
                case '\u00A6':
                    out.append("&brvbar;");
                    break;
                case '\u00A7':
                    out.append("&sect;");
                    break;
                case '\u00A8':
                    out.append("&uml;");
                    break;
                case '\u00A9':
                    out.append("&copy;");
                    break;
                case '\u00AA':
                    out.append("&ordf;");
                    break;
                case '\u00AB':
                    out.append("&laquo;");
                    break;
                case '\u00AC':
                    out.append("&not;");
                    break;
                case '\u00AD':
                    out.append("&shy;");
                    break;
                case '\u00AE':
                    out.append("&reg;");
                    break;
                case '\u00AF':
                    out.append("&macr;");
                    break;
                case '\u00B0':
                    out.append("&deg;");
                    break;
                case '\u00B1':
                    out.append("&plusmn;");
                    break;
                case '\u00B2':
                    out.append("&sup2;");
                    break;
                case '\u00B3':
                    out.append("&sup3;");
                    break;
                case '\u00B4':
                    out.append("&acute;");
                    break;
                case '\u00B5':
                    out.append("&micro;");
                    break;
                case '\u00B6':
                    out.append("&para;");
                    break;
                case '\u00B7':
                    out.append("&middot;");
                    break;
                case '\u00B8':
                    out.append("&cedil;");
                    break;
                case '\u00B9':
                    out.append("&sup1;");
                    break;
                case '\u00BA':
                    out.append("&ordm;");
                    break;
                case '\u00BB':
                    out.append("&raquo;");
                    break;
                case '\u00BC':
                    out.append("&frac14;");
                    break;
                case '\u00BD':
                    out.append("&frac12;");
                    break;
                case '\u00BE':
                    out.append("&frac34;");
                    break;
                case '\u00BF':
                    out.append("&iquest;");
                    break;
                case '\u00C0':
                    out.append("&Agrave;");
                    break;
                case '\u00C1':
                    out.append("&Aacute;");
                    break;
                case '\u00C2':
                    out.append("&Acirc;");
                    break;
                case '\u00C3':
                    out.append("&Atilde;");
                    break;
                case '\u00C4':
                    out.append("&Auml;");
                    break;
                case '\u00C5':
                    out.append("&Aring;");
                    break;
                case '\u00C6':
                    out.append("&AElig;");
                    break;
                case '\u00C7':
                    out.append("&Ccedil;");
                    break;
                case '\u00C8':
                    out.append("&Egrave;");
                    break;
                case '\u00C9':
                    out.append("&Eacute;");
                    break;
                case '\u00CA':
                    out.append("&Ecirc;");
                    break;
                case '\u00CB':
                    out.append("&Euml;");
                    break;
                case '\u00CC':
                    out.append("&Igrave;");
                    break;
                case '\u00CD':
                    out.append("&Iacute;");
                    break;
                case '\u00CE':
                    out.append("&Icirc;");
                    break;
                case '\u00CF':
                    out.append("&Iuml;");
                    break;
                case '\u00D0':
                    out.append("&ETH;");
                    break;
                case '\u00D1':
                    out.append("&Ntilde;");
                    break;
                case '\u00D2':
                    out.append("&Ograve;");
                    break;
                case '\u00D3':
                    out.append("&Oacute;");
                    break;
                case '\u00D4':
                    out.append("&Ocirc;");
                    break;
                case '\u00D5':
                    out.append("&Otilde;");
                    break;
                case '\u00D6':
                    out.append("&Ouml;");
                    break;
                case '\u00D7':
                    out.append("&times;");
                    break;
                case '\u00D8':
                    out.append("&Oslash;");
                    break;
                case '\u00D9':
                    out.append("&Ugrave;");
                    break;
                case '\u00DA':
                    out.append("&Uacute;");
                    break;
                case '\u00DB':
                    out.append("&Ucirc;");
                    break;
                case '\u00DC':
                    out.append("&Uuml;");
                    break;
                case '\u00DD':
                    out.append("&Yacute;");
                    break;
                case '\u00DE':
                    out.append("&THORN;");
                    break;
                case '\u00DF':
                    out.append("&szlig;");
                    break;
                case '\u00E0':
                    out.append("&agrave;");
                    break;
                case '\u00E1':
                    out.append("&aacute;");
                    break;
                case '\u00E2':
                    out.append("&acirc;");
                    break;
                case '\u00E3':
                    out.append("&atilde;");
                    break;
                case '\u00E4':
                    out.append("&auml;");
                    break;
                case '\u00E5':
                    out.append("&aring;");
                    break;
                case '\u00E6':
                    out.append("&aelig;");
                    break;
                case '\u00E7':
                    out.append("&ccedil;");
                    break;
                case '\u00E8':
                    out.append("&egrave;");
                    break;
                case '\u00E9':
                    out.append("&eacute;");
                    break;
                case '\u00EA':
                    out.append("&ecirc;");
                    break;
                case '\u00EB':
                    out.append("&euml;");
                    break;
                case '\u00EC':
                    out.append("&igrave;");
                    break;
                case '\u00ED':
                    out.append("&iacute;");
                    break;
                case '\u00EE':
                    out.append("&icirc;");
                    break;
                case '\u00EF':
                    out.append("&iuml;");
                    break;
                case '\u00F0':
                    out.append("&eth;");
                    break;
                case '\u00F1':
                    out.append("&ntilde;");
                    break;
                case '\u00F2':
                    out.append("&ograve;");
                    break;
                case '\u00F3':
                    out.append("&oacute;");
                    break;
                case '\u00F4':
                    out.append("&ocirc;");
                    break;
                case '\u00F5':
                    out.append("&otilde;");
                    break;
                case '\u00F6':
                    out.append("&ouml;");
                    break;
                case '\u00F7':
                    out.append("&divide;");
                    break;
                case '\u00F8':
                    out.append("&oslash;");
                    break;
                case '\u00F9':
                    out.append("&ugrave;");
                    break;
                case '\u00FA':
                    out.append("&uacute;");
                    break;
                case '\u00FB':
                    out.append("&ucirc;");
                    break;
                case '\u00FC':
                    out.append("&uuml;");
                    break;
                case '\u00FD':
                    out.append("&yacute;");
                    break;
                case '\u00FE':
                    out.append("&thorn;");
                    break;
                case '\u00FF':
                    out.append("&yuml;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
