package views {

    package object context {

      def flash = play.mvc.Scope.Flash.current()

    }

}
