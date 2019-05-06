package macee;

import java.util.UUID;
import macee.annotations.ExaminationPhase;
import macee.annotations.PartitionMode;
import macee.annotations.components.ComponentType;
import macee.core.Lifecycle;
import macee.core.util.Identity;
import macee.core.util.ObjectDescription;

/**
 * Representa um procedimento do exame.
 * 
 * COMENTÁRIO (Werneck): a interface Lifecycle define métodos de inicialização (setup) e
 * término do procedimento (shutdown). Acho que a interface é conflitante/tem sobreposição com a
 * Task do IPED. Como o procedimento pode ser instanciado várias vezes (por worker, por thread, remotamente),
 * ele tem um UUID. 
 * 
 * IMPORTANTE: parece-me que ExaminationProcedure deveria ser uma instanciação de uma classe
 * anotada com ExaminationComponent (por isso o método getComponent()). Não me lembro
 * porque está comentado. Os métodos getType, getPhase e getPartitionMode podem
 * ser implementados com métodos default usando getComponent().type(), por exemplo.
 * 
 * O recurso de checkpoint verifica se é possível salvar o estado do processamento
 * e interromper/retomar o procedimento. Creio que nem toda Task terá esse
 * recurso, portanto, seria melhor colocar esses métodos em uma interface própria (Checkpoint)
 * e verificar se a Task implementa a interface (instanceof). O mesmo pode ser feito para
 * recursos de timeout e retry.
 *
 * @author WERNECK
 */
public interface ExaminationProcedure extends Lifecycle, Identity<UUID>, ObjectDescription {

    ForensicModule getModule();

    //    ExaminationComponent getComponent();
    void checkpoint() throws Exception;

    int getCheckpointThreshold();

    void prepare() throws Exception;

    void finish() throws Exception;

    ComponentType getType();

    ExaminationPhase getPhase();

    PartitionMode getPartitionMode();

    String guid();
}
