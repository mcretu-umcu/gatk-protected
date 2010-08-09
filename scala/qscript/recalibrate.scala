import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.QScript
import org.apache.commons.io.FilenameUtils;

class recalibrate extends QScript {
  @Input(doc="bamIn", shortName="I")
  var bamIns: List[File] = Nil
  
  @Argument(doc="scatter")
  var scatter = false

  @Argument(doc="gatk jar file")
  var gatkJarFile: File = _

def script = {
    for (bamIn <- bamIns) {
      val root = bamIn.getPath()
      val bamRoot = FilenameUtils.removeExtension(root);
      val recalData = new File(bamRoot + ".recal_data.csv")
      val recalBam = new File(bamRoot + ".recal.bam")
      val recalRecalData = new File(bamRoot + ".recal.recal_data.csv")
      //add(new CountCovariates(root, recalData, "-OQ"))
      val tableRecal = new TableRecalibrate(bamIn, recalData, recalBam) { useOriginalQualities = true }
      if ( scatter ) {
            tableRecal.intervals = new File("/humgen/gsa-hpprojects/GATK/data/chromosomes.hg18.interval_list")
      	    tableRecal.scatterCount = 25
      }
      add(tableRecal)
      add(new Index(recalBam))
      add(new CountCovariates(recalBam, recalRecalData) { num_threads = Some(4) })
      add(new AnalyzeCovariates(recalData, new File(recalData.getPath() + ".analyzeCovariates")))
      add(new AnalyzeCovariates(recalRecalData, new File(recalRecalData.getPath() + ".analyzeCovariates")))
    }
}

def bai(bam: File) = new File(bam + ".bai")

class Index(bamIn: File) extends BamIndexFunction {
    bamFile = bamIn
}

class CountCovariates(bamIn: File, recalDataIn: File) extends org.broadinstitute.sting.queue.extensions.gatk.CountCovariates {
    this.jarFile = gatkJarFile
    this.input_file :+= bamIn
    this.recal_file = recalDataIn
    this.DBSNP = new File("/humgen/gsa-hpprojects/GATK/data/dbsnp_129_hg18.rod")
    this.logging_level = "INFO"
    this.max_reads_at_locus = Some(20000)
    this.covariate ++= List("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "DinucCovariate")
    this.memoryLimit = Some(4)

    override def dotString = "CountCovariates: %s [args %s]".format(bamIn.getName, if (this.num_threads.isDefined) "-nt " + this.num_threads else "")
}

class TableRecalibrate(bamInArg: File, recalDataIn: File, bamOutArg: File) extends org.broadinstitute.sting.queue.extensions.gatk.TableRecalibration {
    this.jarFile = gatkJarFile
    this.input_file :+= bamInArg
    this.recal_file = recalDataIn
    this.output_bam = bamOutArg
    this.logging_level = "INFO"
    this.memoryLimit = Some(2)

    override def dotString = "TableRecalibrate: %s => %s".format(bamInArg.getName, bamOutArg.getName, if (this.useOriginalQualities) " -OQ" else "")
}

class AnalyzeCovariates(recalDataIn: File, outputDir: File) extends  org.broadinstitute.sting.queue.extensions.gatk.AnalyzeCovariates {
    this.jarFile = new File("/home/radon01/depristo/dev/GenomeAnalysisTK/trunk/dist/AnalyzeCovariates.jar")
    this.recal_file = recalDataIn
    this.output_dir = outputDir.toString
    this.path_to_resources = "/home/radon01/depristo/dev/GenomeAnalysisTK/trunk/R/"
    this.ignoreQ = Some(5)
    this.path_to_Rscript = "/broad/tools/apps/R-2.6.0/bin/Rscript"
    this.memoryLimit = Some(4)

    override def dotString = "AnalyzeCovariates: %s".format(recalDataIn.getName)
}
}
