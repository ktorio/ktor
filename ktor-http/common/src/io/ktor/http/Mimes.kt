/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*

internal const val INITIAL_MIMES_LIST_SIZE: Int = 1215

private val rawMimes: String
    get() = """
application/acad,dwg
application/andrew-inset,ez
application/applixware,aw
application/arj,arj
application/atom+xml,atom
application/atomcat+xml,atomcat
application/atomsvc+xml,atomsvc
application/base64,mm mme
application/binhex,hqx
application/binhex4,hqx
application/book,boo book
application/ccxml+xml,ccxml
application/cdf,cdf
application/cdmi-capability,cdmia
application/cdmi-container,cdmic
application/cdmi-domain,cdmid
application/cdmi-object,cdmio
application/cdmi-queue,cdmiq
application/clariscad,ccad
application/commonground,dp
application/cu-seeme,cu
application/davmount+xml,davmount
application/drafting,drw
application/dsptype,tsp
application/dssc+der,dssc
application/dssc+xml,xdssc
application/dxf,dxf
application/emma+xml,emma
application/envoy,evy
application/epub+zip,epub
application/excel,xl xla xlb xlc xld xlk xll xlm xls xlt xlv xlw
application/exi,exi
application/font-tdpfr,pfr
application/fractals,fif
application/freeloader,frl
application/futuresplash,spl
application/gnutar,tgz
application/groupwise,vew
application/gzip,gz gzip
application/hlp,hlp
application/hta,hta
application/hyperstudio,stk
application/i-deas,unv
application/iff,iff
application/iges,iges igs
application/inf,inf
application/ipfix,ipfix
application/java,class
application/java-archive,jar
application/java-byte-code,class
application/java-serialized-object,ser
application/java-vm,class
application/json,json
application/lha,lha
application/lzx,lzx
application/macbinary,bin
application/mac-binary,bin
application/mac-binhex,hqx
application/mac-binhex40,hqx
application/mac-compactpro,cpt
application/mads+xml,mads
application/manifest+json,webmanifest
application/marc,mrc
application/marcxml+xml,mrcx
application/mathematica,ma
application/mathml+xml,mathml
application/mbedlet,mbd
application/mbox,mbox
application/mcad,mcd
application/mediaservercontrol+xml,mscml
application/metalink4+xml,meta4
application/mets+xml,mets
application/mime,aps
application/mods+xml,mods
application/mp21,m21
application/mspowerpoint,pot pps ppt ppz
application/msword,doc dot w6w wiz word
application/mswrite,wri
application/mxf,mxf
application/netmc,mcp
application/octet-stream,a arc arj bin com dump exe keychain lzh lzx o psd saveme sdf uu zoo
application/oda,oda
application/oebps-package+xml,opf
application/ogg,ogx
application/onenote,onetoc
application/patch-ops-error+xml,xer
application/pdf,pdf
application/pgp-encrypted,
application/pgp-keys,key
application/pgp-signature,pgp
application/pics-rules,prf
application/pkcs-12,p12
application/pkcs-crl,crl
application/pkcs10,p10
application/pkcs7-mime,p7c p7m
application/pkcs7-signature,p7s
application/pkcs8,p8
application/pkix-attr-cert,ac
application/pkix-cert,cer crt
application/pkix-crl,crl
application/pkix-pkipath,pkipath
application/pkixcmp,pki
application/pls+xml,pls
application/postscript,ai eps ps
application/powerpoint,ppt
application/pro_eng,part prt
application/prs.cww,cww
application/pskc+xml,pskcxml
application/rdf+xml,rdf
application/reginfo+xml,rif
application/relax-ng-compact-syntax,rnc
application/resource-lists+xml,rl
application/resource-lists-diff+xml,rld
application/ringing-tones,rng
application/rls-services+xml,rs
application/rsd+xml,rsd
application/rss+xml,rss
application/rtf,rtf rtx
application/sbml+xml,sbml
application/scvp-cv-request,scq
application/scvp-cv-response,scs
application/scvp-vp-request,spq
application/scvp-vp-response,spp
application/sdp,sdp
application/sea,sea
application/set,set
application/set-payment-initiation,setpay
application/set-registration-initiation,setreg
application/shf+xml,shf
application/sla,stl
application/smil+xml,smi
application/solids,sol
application/sounder,sdr
application/sparql-query,rq
application/sparql-results+xml,srx
application/srgs+xml,grxml
application/srgs,gram
application/sru+xml,sru
application/ssml+xml,ssml
application/step,step stp
application/streamingmedia,ssm
application/tei+xml,tei
application/thraud+xml,tfi
application/timestamped-data,tsd
application/toolbook,tbk
application/vda,vda
application/vnd.3gpp.pic-bw-large,plb
application/vnd.3gpp.pic-bw-small,psb
application/vnd.3gpp.pic-bw-var,pvb
application/vnd.3gpp2.tcap,tcap
application/vnd.3m.post-it-notes,pwn
application/vnd.accpac.simply.aso,aso
application/vnd.accpac.simply.imp,imp
application/vnd.acucobol,acu
application/vnd.acucorp,atc
application/vnd.adobe.air-application-installer-package+zip,air
application/vnd.adobe.fxp,fxp
application/vnd.adobe.xdp+xml,xdp
application/vnd.adobe.xfdf,xfdf
application/vnd.ahead.space,ahead
application/vnd.airzip.filesecure.azf,azf
application/vnd.airzip.filesecure.azs,azs
application/vnd.amazon.ebook,azw
application/vnd.americandynamics.acc,acc
application/vnd.amiga.ami,ami
application/vnd.android.package-archive,apk
application/vnd.anser-web-certificate-issue-initiation,cii
application/vnd.anser-web-funds-transfer-initiation,fti
application/vnd.antix.game-component,atx
application/vnd.apple.installer+xml,mpkg
application/vnd.apple.mpegurl,m3u8
application/vnd.apple.pages,pages
application/vnd.aristanetworks.swi,swi
application/vnd.audiograph,aep
application/vnd.blueice.multipass,mpm
application/vnd.bmi,bmi
application/vnd.businessobjects,rep
application/vnd.chemdraw+xml,cdxml
application/vnd.chipnuts.karaoke-mmd,mmd
application/vnd.cinderella,cdy
application/vnd.claymore,cla
application/vnd.cloanto.rp9,rp9
application/vnd.clonk.c4group,c4g
application/vnd.cluetrust.cartomobile-config,c11amc
application/vnd.cluetrust.cartomobile-config-pkg,c11amz
application/vnd.commonspace,csp
application/vnd.contact.cmsg,cdbcmsg
application/vnd.cosmocaller,cmc
application/vnd.crick.clicker,clkx
application/vnd.crick.clicker.keyboard,clkk
application/vnd.crick.clicker.palette,clkp
application/vnd.crick.clicker.template,clkt
application/vnd.crick.clicker.wordbank,clkw
application/vnd.criticaltools.wbs+xml,wbs
application/vnd.ctc-posml,pml
application/vnd.cups-ppd,ppd
application/vnd.curl.car,car
application/vnd.curl.pcurl,pcurl
application/vnd.data-vision.rdz,rdz
application/vnd.denovo.fcselayout-link,fe_launch
application/vnd.dna,dna
application/vnd.dolby.mlp,mlp
application/vnd.dpgraph,dpg
application/vnd.dreamfactory,dfac
application/vnd.dvb.ait,ait
application/vnd.dvb.service,svc
application/vnd.dynageo,geo
application/vnd.ecowin.chart,mag
application/vnd.enliven,nml
application/vnd.epson.esf,esf
application/vnd.epson.msf,msf
application/vnd.epson.quickanime,qam
application/vnd.epson.salt,slt
application/vnd.epson.ssf,ssf
application/vnd.eszigno3+xml,es3
application/vnd.ezpix-album,ez2
application/vnd.ezpix-package,ez3
application/vnd.fdf,fdf
application/vnd.fdsn.seed,seed
application/vnd.flographit,gph
application/vnd.fluxtime.clip,ftc
application/vnd.framemaker,fm
application/vnd.fsc.weblaunch,fsc
application/vnd.fujitsu.oasys,oas
application/vnd.fujitsu.oasys2,oa2
application/vnd.fujitsu.oasys3,oa3
application/vnd.fujitsu.oasysgp,fg5
application/vnd.fujitsu.oasysprs,bh2
application/vnd.fujixerox.ddd,ddd
application/vnd.fujixerox.docuworks,xdw
application/vnd.fujixerox.docuworks.binder,xbd
application/vnd.fuzzysheet,fzs
application/vnd.genomatix.tuxedo,txd
application/vnd.geogebra.file,ggb
application/vnd.geogebra.tool,ggt
application/vnd.geometry-explorer,gex
application/vnd.geonext,gxt
application/vnd.geoplan,g2w
application/vnd.geospace,g3w
application/vnd.gmx,gmx
application/vnd.google-earth.kml+xml,kml
application/vnd.google-earth.kmz,kmz
application/vnd.grafeq,gqf
application/vnd.groove-account,gac
application/vnd.groove-help,ghf
application/vnd.groove-identity-message,gim
application/vnd.groove-injector,grv
application/vnd.groove-tool-message,gtm
application/vnd.groove-tool-template,tpl
application/vnd.groove-vcard,vcg
application/vnd.hal+xml,hal
application/vnd.handheld-entertainment+xml,zmm
application/vnd.hbci,hbci
application/vnd.hhe.lesson-player,les
application/vnd.hp-hpgl,hgl hpg hpgl
application/vnd.hp-hpid,hpid
application/vnd.hp-hps,hps
application/vnd.hp-jlyt,jlt
application/vnd.hp-pcl,pcl
application/vnd.hp-pclxl,pclxl
application/vnd.hydrostatix.sof-data,sfd-hdstx
application/vnd.hzn-3d-crossword,x3d
application/vnd.ibm.minipay,mpy
application/vnd.ibm.rights-management,irm
application/vnd.ibm.secure-container,sc
application/vnd.iccprofile,icc
application/vnd.igloader,igl
application/vnd.immervision-ivp,ivp
application/vnd.immervision-ivu,ivu
application/vnd.insors.igm,igm
application/vnd.intercon.formnet,xpw
application/vnd.intergeo,i2g
application/vnd.intu.qbo,qbo
application/vnd.intu.qfx,qfx
application/vnd.ipunplugged.rcprofile,rcprofile
application/vnd.irepository.package+xml,irp
application/vnd.is-xpr,xpr
application/vnd.isac.fcs,fcs
application/vnd.jam,jam
application/vnd.jcp.javame.midlet-rms,rms
application/vnd.jisp,jisp
application/vnd.joost.joda-archive,joda
application/vnd.kahootz,ktz
application/vnd.kde.karbon,karbon
application/vnd.kde.kchart,chrt
application/vnd.kde.kformula,kfo
application/vnd.kde.kivio,flw
application/vnd.kde.kontour,kon
application/vnd.kde.kpresenter,kpr
application/vnd.kde.kspread,ksp
application/vnd.kde.kword,kwd
application/vnd.kenameaapp,htke
application/vnd.kidspiration,kia
application/vnd.kinar,kne
application/vnd.koan,skp
application/vnd.kodak-descriptor,sse
application/vnd.las.las+xml,lasxml
application/vnd.llamagraphics.life-balance.desktop,lbd
application/vnd.llamagraphics.life-balance.exchange+xml,lbe
application/vnd.lotus-1-2-3,123
application/vnd.lotus-approach,apr
application/vnd.lotus-freelance,pre
application/vnd.lotus-notes,nsf
application/vnd.lotus-organizer,org
application/vnd.lotus-screencam,scm
application/vnd.lotus-wordpro,lwp
application/vnd.macports.portpkg,portpkg
application/vnd.mcd,mcd
application/vnd.medcalcdata,mc1
application/vnd.mediastation.cdkey,cdkey
application/vnd.mfer,mwf
application/vnd.mfmp,mfm
application/vnd.micrografx.flo,flo
application/vnd.micrografx.igx,igx
application/vnd.mif,mif
application/vnd.mobius.daf,daf
application/vnd.mobius.dis,dis
application/vnd.mobius.mbk,mbk
application/vnd.mobius.mqy,mqy
application/vnd.mobius.msl,msl
application/vnd.mobius.plc,plc
application/vnd.mobius.txf,txf
application/vnd.mophun.application,mpn
application/vnd.mophun.certificate,mpc
application/vnd.mozilla.xul+xml,xul
application/vnd.ms-artgalry,cil
application/vnd.ms-cab-compressed,cab
application/vnd.ms-excel,xlb xlc xll xlm xls xlw
application/vnd.ms-excel.addin.macroenabled.12,xlam
application/vnd.ms-excel.sheet.binary.macroenabled.12,xlsb
application/vnd.ms-excel.sheet.macroenabled.12,xlsm
application/vnd.ms-excel.template.macroenabled.12,xltm
application/vnd.ms-fontobject,eot
application/vnd.ms-htmlhelp,chm
application/vnd.ms-ims,ims
application/vnd.ms-lrm,lrm
application/vnd.ms-officetheme,thmx
application/vnd.ms-outlook,msg
application/vnd.ms-pki.certstore,sst
application/vnd.ms-pki.pko,pko
application/vnd.ms-pki.seccat,cat
application/vnd.ms-pki.stl,stl
application/vnd.ms-powerpoint,pot ppa pps ppt pwz
application/vnd.ms-powerpoint.addin.macroenabled.12,ppam
application/vnd.ms-powerpoint.presentation.macroenabled.12,pptm
application/vnd.ms-powerpoint.slide.macroenabled.12,sldm
application/vnd.ms-powerpoint.slideshow.macroenabled.12,ppsm
application/vnd.ms-powerpoint.template.macroenabled.12,potm
application/vnd.ms-project,mpp
application/vnd.ms-word.document.macroenabled.12,docm
application/vnd.ms-word.template.macroenabled.12,dotm
application/vnd.ms-works,wps
application/vnd.ms-wpl,wpl
application/vnd.ms-xpsdocument,xps
application/vnd.mseq,mseq
application/vnd.musician,mus
application/vnd.muvee.style,msty
application/vnd.neurolanguage.nlu,nlu
application/vnd.noblenet-directory,nnd
application/vnd.noblenet-sealer,nns
application/vnd.noblenet-web,nnw
application/vnd.nokia.configuration-message,ncm
application/vnd.nokia.n-gage.data,ngdat
application/vnd.nokia.radio-preset,rpst
application/vnd.nokia.radio-presets,rpss
application/vnd.nokia.ringing-tone,rng
application/vnd.novadigm.edm,edm
application/vnd.novadigm.edx,edx
application/vnd.novadigm.ext,ext
application/vnd.oasis.opendocument.chart,odc
application/vnd.oasis.opendocument.chart-template,otc
application/vnd.oasis.opendocument.formula,odf
application/vnd.oasis.opendocument.formula-template,odft
application/vnd.oasis.opendocument.graphics,odg
application/vnd.oasis.opendocument.graphics-template,otg
application/vnd.oasis.opendocument.image,odi
application/vnd.oasis.opendocument.image-template,oti
application/vnd.oasis.opendocument.presentation,odp
application/vnd.oasis.opendocument.presentation-template,otp
application/vnd.oasis.opendocument.spreadsheet,ods
application/vnd.oasis.opendocument.spreadsheet-template,ots
application/vnd.oasis.opendocument.text,odt
application/vnd.oasis.opendocument.text-master,odm
application/vnd.oasis.opendocument.text-template,ott
application/vnd.oasis.opendocument.text-web,oth
application/vnd.olpc-sugar,xo
application/vnd.oma.dd2+xml,dd2
application/vnd.openofficeorg.extension,oxt
application/vnd.openxmlformats-officedocument.presentationml.presentation,pptx
application/vnd.openxmlformats-officedocument.presentationml.slide,sldx
application/vnd.openxmlformats-officedocument.presentationml.slideshow,ppsx
application/vnd.openxmlformats-officedocument.presentationml.template,potx
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,xlsx
application/vnd.openxmlformats-officedocument.spreadsheetml.template,xltx
application/vnd.openxmlformats-officedocument.wordprocessingml.document,docx
application/vnd.openxmlformats-officedocument.wordprocessingml.template,dotx
application/vnd.osgeo.mapguide.package,mgp
application/vnd.osgi.dp,dp
application/vnd.palm,pdb
application/vnd.pawaafile,paw
application/vnd.pg.format,str
application/vnd.pg.osasli,ei6
application/vnd.picsel,efif
application/vnd.pmi.widget,wg
application/vnd.pocketlearn,plf
application/vnd.powerbuilder6,pbd
application/vnd.previewsystems.box,box
application/vnd.proteus.magazine,mgz
application/vnd.publishare-delta-tree,qps
application/vnd.pvi.ptid1,ptid
application/vnd.quark.quarkxpress,qxd
application/vnd.realvnc.bed,bed
application/vnd.recordare.musicxml+xml,musicxml
application/vnd.recordare.musicxml,mxl
application/vnd.rig.cryptonote,cryptonote
application/vnd.rim.cod,cod
application/vnd.rn-realmedia,rm
application/vnd.rn-realplayer,rnx
application/vnd.route66.link66+xml,link66
application/vnd.sailingtracker.track,st
application/vnd.seemail,see
application/vnd.sema,sema
application/vnd.semd,semd
application/vnd.semf,semf
application/vnd.shana.informed.formdata,ifm
application/vnd.shana.informed.formtemplate,itp
application/vnd.shana.informed.interchange,iif
application/vnd.shana.informed.package,ipk
application/vnd.simtech-mindmapper,twd
application/vnd.smaf,mmf
application/vnd.smart.teacher,teacher
application/vnd.solent.sdkm+xml,sdkm
application/vnd.spotfire.dxp,dxp
application/vnd.spotfire.sfs,sfs
application/vnd.stardivision.calc,sdc
application/vnd.stardivision.draw,sda
application/vnd.stardivision.impress,sdd
application/vnd.stardivision.math,smf
application/vnd.stardivision.writer,sdw
application/vnd.stardivision.writer-global,sgl
application/vnd.stepmania.stepchart,sm
application/vnd.sun.xml.calc,sxc
application/vnd.sun.xml.calc.template,stc
application/vnd.sun.xml.draw,sxd
application/vnd.sun.xml.draw.template,std
application/vnd.sun.xml.impress,sxi
application/vnd.sun.xml.impress.template,sti
application/vnd.sun.xml.math,sxm
application/vnd.sun.xml.writer,sxw
application/vnd.sun.xml.writer.global,sxg
application/vnd.sun.xml.writer.template,stw
application/vnd.sus-calendar,sus
application/vnd.svd,svd
application/vnd.symbian.install,sis
application/vnd.syncml+xml,xsm
application/vnd.syncml.dm+wbxml,bdm
application/vnd.syncml.dm+xml,xdm
application/vnd.tao.intent-module-archive,tao
application/vnd.tmobile-livetv,tmo
application/vnd.trid.tpt,tpt
application/vnd.triscape.mxs,mxs
application/vnd.trueapp,tra
application/vnd.ufdl,ufd
application/vnd.uiq.theme,utz
application/vnd.umajin,umj
application/vnd.unity,unityweb
application/vnd.uoml+xml,uoml
application/vnd.vcx,vcx
application/vnd.visio,vsd
application/vnd.visionary,vis
application/vnd.vsf,vsf
application/vnd.wap.wbxml,wbxml
application/vnd.wap.wmlc,wmlc
application/vnd.wap.wmlscriptc,wmlsc
application/vnd.webturbo,wtb
application/vnd.wolfram.player,nbp
application/vnd.wordperfect,wpd
application/vnd.wqd,wqd
application/vnd.wt.stf,stf
application/vnd.xara,web xar
application/vnd.xfdl,xfdl
application/vnd.yamaha.hv-dic,hvd
application/vnd.yamaha.hv-script,hvs
application/vnd.yamaha.hv-voice,hvp
application/vnd.yamaha.openscoreformat,osf
application/vnd.yamaha.openscoreformat.osfpvg+xml,osfpvg
application/vnd.yamaha.smaf-audio,saf
application/vnd.yamaha.smaf-phrase,spf
application/vnd.yellowriver-custom-menu,cmp
application/vnd.zul,zir
application/vnd.zzazz.deck+xml,zaz
application/vocaltec-media-desc,vmd
application/vocaltec-media-file,vmf
application/voicexml+xml,vxml
application/wasm,wasm
application/widget,wgt
application/winhlp,hlp
application/wordperfect,wp wp5 wp6 wpd
application/wordperfect6.0,w60 wp5
application/wordperfect6.1,w61
application/wsdl+xml,wsdl
application/wspolicy+xml,wspolicy
application/x-123,wk1
application/x-7z-compressed,7z
application/x-abiword,abw
application/x-ace-compressed,ace
application/x-aim,aim
application/x-authorware-bin,aab
application/x-authorware-map,aam
application/x-authorware-seg,aas
application/x-bcpio,bcpio
application/x-binary,bin
application/x-binhex40,hqx
application/x-bittorrent,torrent
application/x-bsh,bsh sh shar
application/x-bytecode.python,pyc
application/x-bzip,bz
application/x-bzip2,boz bz2
application/x-cdf,cdf
application/x-cdlink,vcd
application/x-chat,cha chat
application/x-chess-pgn,pgn
application/x-cmu-raster,ras
application/x-cocoa,cco
application/x-compactpro,cpt
application/x-compress,z
application/zip,zip
application/x-compressed,gz tgz z zip
application/x-conference,nsc
application/x-cpio,cpio
application/x-cpt,cpt
application/x-csh,csh
application/x-debian-package,deb
application/x-deepv,deepv
application/x-director,dcr dir dxr
application/x-doom,wad
application/x-dtbncx+xml,ncx
application/x-dtbook+xml,dtb
application/x-dtbresource+xml,res
application/x-dvi,dvi
application/x-elc,elc
application/x-envoy,env evy
application/x-esrehber,es
application/x-excel,xla xlb xlc xld xlk xll xlm xls xlt xlv xlw
application/x-font-bdf,bdf
application/x-font-ghostscript,gsf
application/x-font-linux-psf,psf
application/x-font-pcf,pcf
application/x-font-snf,snf
application/x-font-type1,pfa
application/x-frame,mif
application/x-freelance,pre
application/x-futuresplash,spl
application/x-gnumeric,gnumeric
application/x-gsp,gsp
application/x-gss,gss
application/x-gtar,gtar
application/x-gzip,gz gzip
application/x-hdf,hdf
application/x-helpfile,help hlp
application/x-httpd-imap,imap
application/x-ima,ima
application/x-internett-signup,ins
application/x-inventor,iv
application/x-ip2,ip
application/x-java-class,class
application/x-java-commerce,jcm
application/x-java-jnlp-file,jnlp
application/x-koan,skd skm skp skt
application/x-ksh,ksh
application/x-latex,latex ltx
application/x-lha,lha
application/x-lisp,lsp
application/x-livescreen,ivy
application/x-lotus,wq1
application/x-lotusscreencam,scm
application/x-lzh,lzh
application/x-lzx,lzx
application/x-mac-binhex40,hqx
application/x-macbinary,bin
application/x-magic-cap-package-1.0,mc${'$'}
application/x-mathcad,mcd
application/x-meme,mm
application/x-midi,mid midi
application/x-mif,mif
application/x-mix-transfer,nix
application/x-mobipocket-ebook,prc
application/x-mplayer2,asx
application/x-ms-application,application
application/x-ms-wmd,wmd
application/x-ms-wmz,wmz
application/x-ms-xbap,xbap
application/x-msaccess,mdb
application/x-msbinder,obd
application/x-mscardfile,crd
application/x-msclip,clp
application/x-msdownload,exe
application/x-msexcel,xla xls xlw
application/x-msmediaview,mvb
application/x-msmetafile,wmf
application/x-msmoney,mny
application/x-mspowerpoint,ppt
application/x-mspublisher,pub
application/x-msschedule,scd
application/x-msterminal,trm
application/x-mswrite,wri
application/x-navi-animation,ani
application/x-navidoc,nvd
application/x-navimap,map
application/x-navistyle,stl
application/x-netcdf,cdf nc
application/x-newton-compatible-pkg,pkg
application/x-nokia-9000-communicator-add-on-software,aos
application/x-omc,omc
application/x-omcdatamaker,omcd
application/x-omcregerator,omcr
application/x-pagemaker,pm4 pm5
application/x-pcl,pcl
application/x-pem-file,pem
application/x-pixclscript,plx
application/x-pkcs12,pfx
application/x-pkcs7-certificates,p7b spc
application/x-pkcs7-certreqresp,p7r
application/x-pkcs7-signature,p7a
application/x-portable-anymap,pnm
application/x-project,mpc mpt mpv mpx
application/x-qpro,wb1
application/x-rar-compressed,rar
application/x-sdp,sdp
application/x-sea,sea
application/x-seelogo,sl
application/x-sh,sh
application/x-shar,sh shar
application/x-shockwave-flash,swf
application/x-silverlight-app,xap
application/x-sit,sit
application/x-sprite,spr sprite
application/x-stuffit,sit
application/x-stuffitx,sitx
application/x-sv4cpio,sv4cpio
application/x-sv4crc,sv4crc
application/x-tar,tar
application/x-tbook,sbk tbk
application/x-tcl,tcl
application/x-tex,tex
application/x-tex-tfm,tfm
application/x-texinfo,texi texinfo
application/x-troff,roff t tr
application/x-troff-man,man
application/x-troff-me,me
application/x-troff-ms,ms
application/x-ustar,ustar
application/x-visio,vsd vst vsw
application/x-vnd.audioexplosion.mzz,mzz
application/x-vnd.ls-xpix,xpix
application/x-vrml,vrml
application/x-wais-source,src wsrc
application/x-winhelp,hlp
application/x-wintalk,wtk
application/x-world,svr wrl
application/x-wpwin,wpd
application/x-wri,wri
application/x-x509-ca-cert,crt der
application/x-x509-user-cert,crt
application/x-xfig,fig
application/x-xpinstall,xpi
application/x-xz,xz
application/x-zip-compressed,zip
application/xcap-diff+xml,xdf
application/xenc+xml,xenc
application/xhtml+xml,xhtml
application/xml,xml
application/xml-dtd,dtd
application/xop+xml,xop
application/xslt+xml,xslt
application/xspf+xml,xspf
application/xv+xml,mxml
application/yang,yang
application/yaml,yaml
application/x-yaml,yaml
application/yin+xml,yin
application/zip,war
audio/aac,aac
audio/adpcm,adp
audio/aiff,aif aifc aiff
audio/basic,au snd
audio/it,it
audio/make,funk my pfunk
audio/make.my.funk,pfunk
audio/mid,rmi
audio/midi,kar mid midi
audio/mod,mod
audio/mp4,m4a mp4a
audio/mpeg,m2a mp2 mp3 mpa mpga
audio/mpeg3,mp3
audio/nspaudio,la lma
audio/ogg,oga ogg
audio/s3m,s3m
audio/tsp-audio,tsi
audio/tsplayer,tsp
audio/vnd.dece.audio,uva
audio/vnd.digital-winds,eol
audio/vnd.dra,dra
audio/vnd.dts,dts
audio/vnd.dts.hd,dtshd
audio/vnd.lucent.voice,lvp
audio/vnd.ms-playready.media.pya,pya
audio/vnd.nuera.ecelp4800,ecelp4800
audio/vnd.nuera.ecelp7470,ecelp7470
audio/vnd.nuera.ecelp9600,ecelp9600
audio/vnd.qcelp,qcp
audio/vnd.rip,rip
audio/voc,voc
audio/voxware,vox
audio/wav,wav
audio/webm,weba
audio/x-adpcm,snd
audio/x-au,au
audio/x-gsm,gsd gsm
audio/x-jam,jam
audio/x-liveaudio,lam
audio/x-mid,mid midi
audio/x-midi,midi
audio/x-mod,mod
audio/x-mpeg,mp2
audio/x-mpegurl,m3u
audio/x-ms-wax,wax
audio/x-ms-wma,wma
audio/x-nspaudio,la lma
audio/x-pn-realaudio,ra ram rm rmm rmp
audio/x-pn-realaudio-plugin,ra rmp rpm
application/x-rpm,rpm
audio/x-psid,sid
audio/x-realaudio,ra
audio/x-twinvq,vqf
audio/x-twinvq-plugin,vqe vql
audio/x-vnd.audioexplosion.mjuicemediafile,mjf
audio/x-voc,voc
audio/xm,xm
binary/octet-stream,dat
chemical/x-cdx,cdx
chemical/x-cif,cif
chemical/x-cmdf,cmdf
chemical/x-cml,cml
chemical/x-csml,csml
chemical/x-pdb,pdb xyz
chemical/x-xyz,xyz
font/collection	,collection
font/otf,otf
font/sfnt,sfnt
font/ttf,ttf
font/woff,woff
font/woff2,woff2
i-world/i-vrml,ivr
image/avif,avif avifs
image/bmp,bm bmp
image/cgm,cgm
image/cmu-raster,ras rast
image/fif,fif
image/florian,flo turbot
image/g3fax,g3
image/gif,gif
image/heic,heic
image/heif,heif
image/ief,ief iefs
image/jpeg,jfif jfif-tbnl jpe jpeg jpg
image/jutvision,jut
image/ktx,ktx
image/naplps,nap naplps
image/pict,pic pict
image/pjpeg,jfif
image/png,png x-png
image/prs.btif,btif
image/svg+xml,svg
image/tiff,tif tiff
image/vasa,mcf
image/vnd.adobe.photoshop,psd
image/vnd.dece.graphic,uvi
image/vnd.djvu,djvu
image/vnd.dvb.subtitle,sub
image/vnd.dwg,dwg dxf svf
image/vnd.dxf,dxf
image/vnd.fastbidsheet,fbs
image/vnd.fpx,fpx
image/vnd.fst,fst
image/vnd.fujixerox.edmics-mmr,mmr
image/vnd.fujixerox.edmics-rlc,rlc
image/vnd.ms-modi,mdi
image/vnd.net-fpx,fpx npx
image/vnd.rn-realflash,rf
image/vnd.rn-realpix,rp
image/vnd.wap.wbmp,wbmp
image/vnd.xiff,xif
image/webp,webp
image/x-cmu-raster,ras
image/x-cmx,cmx
image/x-dwg,dwg dxf svf
image/x-freehand,fh
image/x-icon,ico
image/x-jg,art
image/x-jps,jps
image/x-niff,nif niff
image/x-pcx,pcx
image/x-pict,pct
image/x-portable-anymap,pnm
image/x-portable-bitmap,pbm
image/x-portable-graymap,pgm
image/x-portable-pixmap,ppm
image/x-quicktime,qif qti qtif
image/x-rgb,rgb
image/x-windows-bmp,bmp
image/x-xbitmap,xbm
image/x-xbm,xbm
image/x-xpixmap,pm xpm
image/x-xwd,xwd
image/x-xwindowdump,xwd
image/xbm,xbm
image/xpm,xpm
message/rfc822,eml mht mhtml mime
model/iges,iges igs
model/mesh,msh
model/vnd.collada+xml,dae
model/vnd.dwf,dwf
model/vnd.gdl,gdl
model/vnd.gtw,gtw
model/vnd.mts,mts
model/vnd.vtu,vtu
model/vrml,vrml wrl wrz
model/x-pov,pov
multipart/x-gzip,gzip
multipart/x-ustar,ustar
multipart/x-zip,zip
music/crescendo,mid midi
music/x-karaoke,kar
paleovu/x-pv,pvu
text/asp,asp
text/calendar,ics
text/css,css
text/csv,csv
text/html,acgi htm html htmls htx shtml
text/javascript,js mjs
text/mcf,mcf
text/n3,n3
text/pascal,pas
text/plain,c c++ cc com conf cxx def f f90 for g h hh idc jav java list log lst m mar pl sdml text txt
text/plain-bas,par
text/prs.lines.tag,dsc
text/richtext,rt rtx
text/rtf,rtf
text/scriplet,wsc
text/sgml,sgm sgml
text/srt,srt
text/tab-separated-values,tsv
text/troff,t
text/turtle,ttl
text/uri-list,uni unis uri uris
text/vnd.abc,abc
text/vnd.curl,curl
text/vnd.curl.dcurl,dcurl
text/vnd.curl.mcurl,mcurl
text/vnd.curl.scurl,scurl
text/vnd.fly,fly
text/vnd.fmi.flexstor,flx
text/vnd.graphviz,gv
text/vnd.in3d.3dml,3dml
text/vnd.in3d.spot,spot
text/vnd.rn-realtext,rt
text/vnd.sun.j2me.app-descriptor,jad
text/vnd.wap.wml,wml
text/vnd.wap.wmlscript,wmls
text/vtt,vtt
text/webviewhtml,htt
text/x-asm,asm s
text/x-audiosoft-intra,aip
text/x-c,c cc cpp
text/x-component,htc
text/x-fortran,f f77 f90 for
text/x-h,h hh
text/x-java-source,jav java
text/x-la-asf,lsx
text/x-m,m
text/x-pascal,p
text/x-script,hlb
text/x-script.csh,csh
text/x-script.elisp,el
text/x-script.guile,scm
text/x-script.ksh,ksh
text/x-script.lisp,lsp
text/x-script.perl,pl
text/x-script.perl-module,pm
text/x-script.python,py
text/x-script.rexx,rexx
text/x-script.scheme,scm
text/x-script.sh,sh
text/x-script.tcl,tcl
text/x-script.tcsh,tcsh
text/x-script.zsh,zsh
text/x-server-parsed-html,shtml ssi
text/x-setext,etx
text/x-sgml,sgm sgml
text/x-speech,spc talk
text/x-uil,uil
text/x-uuencode,uu uue
text/x-vcalendar,vcs
text/x-vcard,vcf
text/xml,xml
text/yaml,yaml
text/x-yaml,yaml
video/3gpp,3gp
video/3gpp2,3g2
video/animaflex,afl
video/avi,avi
video/avs-video,avs
video/dl,dl
video/dvd,vob
video/fli,fli
video/gl,gl
video/h261,h261
video/h263,h263
video/h264,h264
video/jpeg,jpgv
video/jpm,jpm
video/mj2,mj2
video/mp4,m4v mp4
application/mp4,mp4
video/mpeg,m1v m2v mp2 mpe mpeg mpg
audio/mpeg,mpg
video/msvideo,avi
video/ogg,ogv
video/quicktime,moov mov qt
video/vdo,vdo
video/vivo,viv vivo
video/vnd.dece.hd,uvh
video/vnd.dece.mobile,uvm
video/vnd.dece.pd,uvp
video/vnd.dece.sd,uvs
video/vnd.dece.video,uvv
video/vnd.fvt,fvt
video/vnd.mpegurl,mxu
video/vnd.ms-playready.media.pyv,pyv
video/vnd.rn-realvideo,rv
video/vnd.uvvu.mp4,uvu
video/vnd.vivo,viv vivo
video/vosaic,vos
video/webm,webm
video/x-amt-demorun,xdr
video/x-amt-showrun,xsr
video/x-atomic3d-feature,fmf
video/x-dl,dl
video/x-dv,dif dv
video/x-f4v,f4v
video/x-fli,fli
video/x-flv,flv
video/x-gl,gl
video/x-isvideo,isu
video/x-matroska,mkv
audio/x-matroska,mkv
video/x-motion-jpeg,mjpg
video/x-mpeg,mp2
video/x-mpeq2a,mp2
video/x-ms-asf,asf asx
video/x-ms-asf-plugin,asx
video/x-ms-wm,wm
video/x-ms-wmv,wmv
video/x-ms-wmx,wmx
video/x-ms-wvx,wvx
video/x-msvideo,avi
video/x-qtc,qtc
video/x-scm,scm
video/x-sgi-movie,movie mv
windows/metafile,wmf
www/mime,mime
x-conference/x-cooltalk,ice
x-music/x-midi,mid midi
x-world/x-3dmf,3dm 3dmf qd3 qd3d
x-world/x-svr,svr
x-world/x-vrml,vrml wrl wrz
x-world/x-vrt,vrt
xgl/drawing,xgz
xgl/movie,xmz
# Deprecated media types
application/ecmascript,es
application/javascript,js
application/smil,smi sml
application/vnd.frogans.fnc,fnc
application/vnd.frogans.ltf,ltf
application/vnd.ibm.modcap,afp
application/vnd.nokia.n-gage.symbian.install,n-gage
application/vnd.oasis.opendocument.database,odb
"""

internal fun loadMimes(): List<Pair<String, ContentType>> {
    return rawMimes.lineSequence().flatMapTo(ArrayList(INITIAL_MIMES_LIST_SIZE)) {
        val line = it.trim()
        if (line.isEmpty() || line.startsWith("#")) return@flatMapTo emptySequence()

        val (mime, extensions) = line.split(",", limit = 2)
        val contentType = mime.toContentType()
        extensions.splitToSequence(" ").map { ext ->
            ext.toLowerCasePreservingASCIIRules() to contentType
        }
    }
}

internal val mimes: List<Pair<String, ContentType>> by lazy { loadMimes() }
