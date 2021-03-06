package org.nlogo.ls

import org.nlogo.nvm.{ConcurrentJob, Procedure, Job}
import org.nlogo.agent.AgentSet
import org.nlogo.api.{JobOwner, Workspace}
import org.nlogo.workspace.AbstractWorkspaceScala

class NotifyingJob(notifyObject: AnyRef,
                   workspace: AbstractWorkspaceScala,
                   owner: JobOwner,
                   agentSet: AgentSet,
                   procedure: Procedure)
extends ConcurrentJob(owner, agentSet, procedure, 0, null, workspace, owner.random) {
  override def finish = {
    super.finish
    notifyObject.synchronized {
      notifyObject.notifyAll
    }
  }

  def waitFor: AnyRef = {
    try {
      while (state != Job.REMOVED && state != Job.DONE) {
        notifyObject.synchronized {
          notifyObject.wait(200)
        }
      }
      // TODO This might cause problems if the job's waitFor is never called. We
      // may get lingering lastLogoExceptions that then cause future jobs to
      // erroneously error.
      val ex = workspace.lastLogoException
      if (ex != null) {
        workspace.clearLastLogoException
        throw ex
      }
      result
    } catch {
      case ex: InterruptedException => null// just fall through
    }
  }

}
