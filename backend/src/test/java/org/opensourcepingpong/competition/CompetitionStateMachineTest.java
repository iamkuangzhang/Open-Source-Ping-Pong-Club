package org.opensourcepingpong.competition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompetitionStateMachineTest {
 @Test void permitsEveryDocumentedTransition(){CompetitionStatus s=CompetitionStatus.DRAFT;for(String action:new String[]{"publish","close","ready","start","confirm","finalize"})s=CompetitionStateMachine.next(s,action);assertEquals(CompetitionStatus.FINISHED,s);}
 @Test void cancelIsOnlyAllowedFromNonTerminalStates(){for(var state:CompetitionStatus.values())if(state!=CompetitionStatus.FINISHED&&state!=CompetitionStatus.CANCELLED)assertEquals(CompetitionStatus.CANCELLED,CompetitionStateMachine.next(state,"cancel"));}
 @Test void rejectsIllegalAndTerminalTransitions(){assertThrows(RuntimeException.class,()->CompetitionStateMachine.next(CompetitionStatus.DRAFT,"finalize"));assertThrows(RuntimeException.class,()->CompetitionStateMachine.next(CompetitionStatus.REGISTRATION,"start"));assertThrows(RuntimeException.class,()->CompetitionStateMachine.next(CompetitionStatus.FINISHED,"start"));assertThrows(RuntimeException.class,()->CompetitionStateMachine.next(CompetitionStatus.FINISHED,"cancel"));assertThrows(RuntimeException.class,()->CompetitionStateMachine.next(CompetitionStatus.CANCELLED,"publish"));}
}
