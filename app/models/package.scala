package models {

  import com.novus.salat._
  import play.Play

  package object play_salat_context {
    implicit val ctx = new Context {
      val name = Some("PlaySalatContext")
    }
    ctx.registerClassLoader(Play.classloader)
  }

}