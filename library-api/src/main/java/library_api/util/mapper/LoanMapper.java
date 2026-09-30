package library_api.util.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mapping;

import library_api.dto.LoanResponse;
import library_api.entity.Loan;

/**
 * Traduccion de Loan a su DTO de salida.
 *
 * Solo hay conversion en un sentido (entidad -> respuesta). No existe un
 * toEntity porque un prestamo no se crea a partir de LoanRequest: el request trae
 * un isbn y un userId, no las entidades Book y User, y el servicio construye el
 * Loan con las entidades que ha cargado de sus repositorios.
 *
 * dueDate no es un campo de Loan sino un metodo derivado (getDueDate()), pero
 * MapStruct lo copia igual porque el componente del record se llama tambien
 * dueDate. No hace falta ningun @Mapping para el: los nombres ya coinciden.
 *
 * Los dos @Mapping de isbn y userId si son necesarios: en la entidad el isbn
 * cuelga de Book (book.isbn) y el id de User (user.id), asi que no se pueden
 * copiar por nombre.
 *
 * overdue y daysOverdue llevan expression porque dependen de "hoy" y no son
 * atributos de la entidad: son metodos (isOverdue / getDaysOverdue) que ya aplican
 * la regla de no contar como vencido un libro devuelto tarde. MapStruct no los
 * resolveria solo porque se llamen distinto que sus campos.
 *
 * Estas dos expresiones usan LocalDate.now() por dentro. Es coherente con el
 * service, que tambien sella una unica fecha por peticion, y el desajuste
 * posible seria de milisegundos en un campo que se expresa en dias.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LoanMapper {

    @Mapping(source = "book.isbn", target = "isbn")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "overdue", expression = "java(loan.isOverdue())")
    @Mapping(target = "daysOverdue", expression = "java(loan.getDaysOverdue())")
    LoanResponse toResponse(Loan loan);
}
