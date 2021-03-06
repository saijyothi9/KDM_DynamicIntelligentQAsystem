import java.io.{BufferedWriter, File, FileWriter}
import scala.io.Source

import org.apache.spark.mllib.feature.{Word2Vec, Word2VecModel}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.feature.{HashingTF, IDF}

import scala.collection.immutable.HashMap

object TF_IDF {

  def main(args: Array[String]): Unit = {

    System.setProperty("hadoop.home.dir", "E:\\winutils")

    val sparkConf = new SparkConf().setAppName("SparkWordCount").setMaster("local[*]")
      .set("spark.driver.memory", "6g").set("spark.executor.memory", "6g")


    val sc = new SparkContext(sparkConf)

    //Reading the Text File
    val documents = sc.textFile("data\\input.txt")

    //tfidf
      //Getting the Lemmatised form of the words in TextFile
      val documentseq = documents.map(f => {
        val lemmatised = CoreNLPLemma.returnLemma(f)
        val splitString = lemmatised.split(" ")
        splitString.toSeq
      })

      //Creating an object of HashingTF Class
      val hashingTF = new HashingTF()

      //Creating Term Frequency of the document
      val tf = hashingTF.transform(documentseq)
      tf.cache()


      val idf = new IDF().fit(tf)

      //Creating Inverse Document Frequency
      val tfidf = idf.transform(tf)

      val tfidfvalues = tfidf.flatMap(f => {
        val ff: Array[String] = f.toString.replace(",[", ";").split(";")
        val values = ff(2).replace("]", "").replace(")", "").split(",")
        values
      })

      val tfidfindex = tfidf.flatMap(f => {
        val ff: Array[String] = f.toString.replace(",[", ";").split(";")
        val indices = ff(1).replace("]", "").replace(")", "").split(",")
        indices
      })

      //tfidf.foreach(f => println(f))

      val filename = new File("data/TF_IDF.txt")
      val writer = new BufferedWriter(new FileWriter(filename))
      val tfidfData = tfidfindex.zip(tfidfvalues)
      tfidfData.foreach(f => println(f))

      var hm = new HashMap[String, Double]

      tfidfData.collect().foreach(f => {
        hm += f._1 -> f._2.toDouble
      })

      val mapp = sc.broadcast(hm)


      val documentData = documentseq.flatMap(_.toList)
      val dd = documentData.map(f => {
        val i = hashingTF.indexOf(f)
        val h = mapp.value
        (f, h(i.toString))
      })

      val dd1 = dd.distinct().sortBy(_._2, false)
      dd1.take(10).foreach(f => {
        val fileUtil = new FileUtil().getStopWordsFromList("stopWords.txt")
        if (!fileUtil.contains(f._1)) {
          //println(f)                                uncomment to print tfidf words
          writer.write(f._1 + "\n")
        }
      })
      writer.close()

      val out = "data/TF_IDF.txt"
      val modelFolder = new File("myModelPath")

      if (modelFolder.exists()) {
        val sameModel = Word2VecModel.load(sc, "myModelPath")
        for (ln <- Source.fromFile(out).getLines) {
          println("---similar words for " + ln + "-->")
          val in = sameModel.findSynonyms(ln, 5)
          for ((synonym, cosineSimilarity) <- in) {
            println(s"$synonym $cosineSimilarity")
          }
        }
      }
      else {
        val word2vec = new Word2Vec().setVectorSize(100).setMinCount(0)

        val model = word2vec.fit(documentseq)
        for (ln <- Source.fromFile(out).getLines) {
          println("---similar words for " + ln + "-->")
          val in = model.findSynonyms(ln, 5)
          for ((synonym, cosineSimilarity) <- in) {
            println(s"$synonym $cosineSimilarity")
          }
        }
        model.getVectors.foreach(f => println(f._1 + ":" + f._2.length))

        // Save and load model
        model.save(sc, "myModelPath")
      }
    }
}