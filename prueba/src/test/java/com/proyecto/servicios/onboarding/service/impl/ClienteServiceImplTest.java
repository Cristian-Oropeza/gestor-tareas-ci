package com.proyecto.servicios.onboarding.service.impl;

import java.util.Collections;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.mockito.ArgumentCaptor;
import com.proyecto.servicios.onboarding.dto.request.ClienteAltaRequest;
import com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest;
import com.proyecto.servicios.onboarding.dto.request.ClienteIdRequest;
import com.proyecto.servicios.onboarding.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.onboarding.dto.response.ClienteResponse;
import com.proyecto.servicios.onboarding.entity.Cuenta;
import com.proyecto.servicios.onboarding.entity.enums.EstatusCuenta;
import com.proyecto.servicios.onboarding.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.onboarding.dto.request.DomicilioRequest;
import com.proyecto.servicios.onboarding.dto.response.ClienteAltaResponse;
import com.proyecto.servicios.onboarding.dto.response.ClienteResponse;
import com.proyecto.servicios.onboarding.dto.response.ClienteResumenResponse;
import com.proyecto.servicios.onboarding.dto.response.CuentaResponse;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Cuenta;
import com.proyecto.servicios.onboarding.entity.enums.EstadoCivil;
import com.proyecto.servicios.onboarding.entity.enums.EstatusCuenta;
import com.proyecto.servicios.onboarding.entity.enums.Sexo;
import com.proyecto.servicios.onboarding.exception.DuplicadoException;
import com.proyecto.servicios.onboarding.exception.ValidacionNegocioException;
import com.proyecto.servicios.onboarding.mapper.ClienteMapper;
import com.proyecto.servicios.onboarding.repository.ClienteRepository;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.service.NumeroCuentaGenerator;
import com.proyecto.servicios.onboarding.service.PaginacionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock ClienteRepository clienteRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ClienteMapper mapper;
    @Mock NumeroCuentaGenerator cuentaGen;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PaginacionFactory paginacionFactory;
    @InjectMocks ClienteServiceImpl service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "saldoInicial", new BigDecimal("0.00"));
    }

    @Test
    void registrar_feliz_creaClienteCuentaUsuario() {
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(false);
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);

        Cliente c = Cliente.builder()
                .id(100L)
                .nombre("Pedro").apellidoPaterno("Testing").apellidoMaterno("Prueba")
                .correo("test@demo.mx").activo(Boolean.TRUE)
                .cuentas(new ArrayList<>())
                .fechaCreacion(java.time.OffsetDateTime.now())
                .build();
        when(mapper.aEntidad(any())).thenReturn(c);
        when(cuentaGen.generar()).thenReturn("014180111111111111");
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$enc");
        when(clienteRepository.save(any())).thenReturn(c);
        when(mapper.aCuentaResponse(any())).thenReturn(new CuentaResponse(1L, "014180111111111111", 100L, new java.math.BigDecimal("0.00"), com.proyecto.servicios.onboarding.entity.enums.EstatusCuenta.ACTIVA, null));

        ClienteAltaResponse r = service.registrar(req());
        assertThat(r.id()).isEqualTo(100L);
        assertThat(r.correo()).isEqualTo("test@demo.mx");
    }

    @Test
    void registrar_lanzaCuandoCurpDuplicada() {
        when(clienteRepository.existsByCurp(anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.registrar(req()))
                .isInstanceOf(DuplicadoException.class);
    }

    @Test
    void registrar_lanzaCuandoRfcDuplicado() {
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.registrar(req()))
                .isInstanceOf(DuplicadoException.class);
    }

    @Test
    void registrar_lanzaCuandoCorreoDuplicadoEnClientes() {
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.registrar(req()))
                .isInstanceOf(DuplicadoException.class);
    }

    @Test
    void registrar_lanzaCuandoMenorDeEdad() {
        LocalDate hace15 = LocalDate.now().minusYears(15);
        assertThatThrownBy(() -> service.registrar(reqConFecha(hace15)))
                .isInstanceOf(ValidacionNegocioException.class);
    }

    @Test
    void consultar_paginaMapeada() {
        Cliente c = Cliente.builder().id(1L).nombre("Juan").build();
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(clienteRepository.findAll(any(Specification.class), eq(pr)))
                .thenReturn(new PageImpl<>(List.of(c)));
        when(mapper.aResumen(c)).thenReturn(new ClienteResumenResponse(1L, "Juan", null, null, null, Boolean.TRUE, null));

        Page<ClienteResumenResponse> r = service.consultar(new ClienteConsultaRequest());
        assertThat(r.getContent()).hasSize(1);
    }

    // ---------- helpers ----------
    private ClienteAltaRequest req() {
        return reqConFecha(LocalDate.of(1995, 5, 10));
    }

    private ClienteAltaRequest reqConFecha(LocalDate fechaNac) {
        return new ClienteAltaRequest(
                "Pedro", null, "Testing", "Prueba",
                fechaNac,
                "TEPP950510HDFABC03", "TEPP950510AB1",
                Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO,
                "test@demo.mx", "5512345678", null,
                "Desarrollador", "DemoCorp",
                new BigDecimal("25000.00"),
                "Test1234!",
                new DomicilioRequest("Reforma","123",null,"Centro","Cuauhtemoc","CDMX","06000","Mexico")
        );
    }

    @Test
    void actualizarParcial_actualizaCorreoYNombre() {
        Cliente c = Cliente.builder().id(1L).nombre("Juan").correo("viejo@x.mx").build();
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.existsByCorreo("nuevo@x.mx")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("nuevo@x.mx")).thenReturn(false);
        when(clienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aResponse(any())).thenReturn(mock(ClienteResponse.class));

        ClienteUpdateRequest req = new ClienteUpdateRequest();
        req.setId(1L);
        req.setCorreo("Nuevo@X.mx");
        req.setNombre("Pedro");

        service.actualizarParcial(req);

        assertThat(c.getCorreo()).isEqualTo("nuevo@x.mx");
        assertThat(c.getNombre()).isEqualTo("Pedro");
    }

    @Test
    void actualizarParcial_lanzaCuandoCorreoDuplicado() {
        Cliente c = Cliente.builder().id(1L).correo("viejo@x.mx").build();
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.existsByCorreo("duplicado@x.mx")).thenReturn(true);

        ClienteUpdateRequest req = new ClienteUpdateRequest();
        req.setId(1L);
        req.setCorreo("duplicado@x.mx");

        assertThatThrownBy(() -> service.actualizarParcial(req))
                .isInstanceOf(com.proyecto.servicios.onboarding.exception.DuplicadoException.class);
    }

    @Test
    void actualizarParcial_lanzaCuandoClienteNoExiste() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());
        ClienteUpdateRequest req = new ClienteUpdateRequest();
        req.setId(99L);

        assertThatThrownBy(() -> service.actualizarParcial(req))
                .isInstanceOf(ClienteNoEncontradoException.class);
    }

    @Test
    void darDeBaja_desactivaClienteCuentasYUsuario() {
        Cuenta ctActiva = Cuenta.builder().estatus(EstatusCuenta.ACTIVA).build();
        Cuenta ctCancelada = Cuenta.builder().estatus(EstatusCuenta.CANCELADA).build();
        com.proyecto.servicios.onboarding.entity.Usuario usuario =
                com.proyecto.servicios.onboarding.entity.Usuario.builder().activo(Boolean.TRUE).build();
        Cliente c = Cliente.builder().id(5L).activo(Boolean.TRUE).build();
        c.setCuentas(new ArrayList<>(List.of(ctActiva, ctCancelada)));
        c.setUsuario(usuario);
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(c));

        ClienteIdRequest req = new ClienteIdRequest();
        req.setId(5L);

        service.darDeBaja(req);

        assertThat(c.getActivo()).isFalse();
        assertThat(ctActiva.getEstatus()).isEqualTo(EstatusCuenta.INACTIVA);
        assertThat(ctCancelada.getEstatus()).isEqualTo(EstatusCuenta.CANCELADA);
        assertThat(usuario.getActivo()).isFalse();
    }

    @Test
    void darDeBaja_lanzaCuandoNoExiste() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());
        ClienteIdRequest req = new ClienteIdRequest();
        req.setId(99L);
        assertThatThrownBy(() -> service.darDeBaja(req))
                .isInstanceOf(ClienteNoEncontradoException.class);
    }


    @Test
    void actualizarParcial_conTodosLosCampos_actualizaTodo() {
        com.proyecto.servicios.onboarding.entity.Domicilio dom =
                com.proyecto.servicios.onboarding.entity.Domicilio.builder()
                        .calle("Vieja").numeroExterior("1").colonia("X")
                        .municipio("X").estado("X").codigoPostal("12345").pais("MX").build();
        com.proyecto.servicios.onboarding.entity.Usuario u =
                com.proyecto.servicios.onboarding.entity.Usuario.builder().correo("viejo@x.mx").build();
        Cliente c = Cliente.builder().id(1L).nombre("Juan").correo("viejo@x.mx").build();
        c.setDomicilio(dom); c.setUsuario(u);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.existsByCorreo(any())).thenReturn(false);
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);
        when(clienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aResponse(any())).thenReturn(mock(ClienteResponse.class));

        ClienteUpdateRequest r = new ClienteUpdateRequest();
        r.setId(1L);
        r.setCorreo("nuevo@x.mx");
        r.setNombre("Pedro ");
        r.setSegundoNombre("Jose ");
        r.setApellidoPaterno("Testing ");
        r.setApellidoMaterno("Prueba ");
        r.setSexo(com.proyecto.servicios.onboarding.entity.enums.Sexo.MASCULINO);
        r.setNacionalidad("Mexicana");
        r.setEstadoCivil(com.proyecto.servicios.onboarding.entity.enums.EstadoCivil.CASADO);
        r.setTelefonoMovil("5512345678");
        r.setTelefonoAlterno("5598765432");
        r.setOcupacion("Dev ");
        r.setEmpresa("Acme ");
        r.setIngresoMensual(new java.math.BigDecimal("35000.00"));
        r.setDomicilio(new com.proyecto.servicios.onboarding.dto.request.DomicilioRequest(
                "Reforma ", "123", "A", "Centro ", "CDMX ", "CDMX ", "06000", "Mexico "));

        service.actualizarParcial(r);

        assertThat(c.getNombre()).isEqualTo("Pedro");
        assertThat(c.getSegundoNombre()).isEqualTo("Jose");
        assertThat(c.getApellidoPaterno()).isEqualTo("Testing");
        assertThat(c.getApellidoMaterno()).isEqualTo("Prueba");
        assertThat(c.getCorreo()).isEqualTo("nuevo@x.mx");
        assertThat(u.getCorreo()).isEqualTo("nuevo@x.mx");
        assertThat(dom.getCalle()).isEqualTo("Reforma");
        assertThat(dom.getCodigoPostal()).isEqualTo("06000");
    }

    @Test
    void actualizarParcial_mismoCorreoNoValidaDuplicado() {
        Cliente c = Cliente.builder().id(1L).correo("test@x.mx").build();
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aResponse(any())).thenReturn(mock(ClienteResponse.class));

        ClienteUpdateRequest r = new ClienteUpdateRequest();
        r.setId(1L);
        r.setCorreo("test@x.mx"); // mismo

        service.actualizarParcial(r);
        // verify que NO consulto por duplicado
        verify(clienteRepository, never()).existsByCorreo(any());
    }


    @Test
    void consultar_todosLosFiltros_ejecutaSpecCompleta() {
        com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest req =
                new com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest();
        req.setId(1L);
        req.setCurp("TEPP950510HDFABC03");
        req.setRfc("TEPP950510AB1");
        req.setCorreo("test@demo.mx");
        req.setNumeroCuenta("014180111111111111");
        req.setNombre("Pedro");
        req.setApellidoPaterno("Testing");
        req.setApellidoMaterno("Prueba");
        req.setActivo(Boolean.TRUE);
        req.setFechaDesde(LocalDate.now().minusDays(30));
        req.setFechaHasta(LocalDate.now());
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(clienteRepository.findAll(any(Specification.class), eq(pr))).thenAnswer(inv -> {
            Specification<Cliente> spec = inv.getArgument(0);
            Root<Cliente> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb); // ejecuta todo el lambda
            return new PageImpl<>(Collections.emptyList());
        });
        service.consultar(req);
    }


    @Test
    void consultar_conStringsBlank_cubreBranchFalse() {
        com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest req =
                new com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest();
        req.setCurp(""); req.setRfc(""); req.setCorreo("");
        req.setNumeroCuenta(""); req.setNombre("");
        req.setApellidoPaterno(""); req.setApellidoMaterno("");
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(clienteRepository.findAll(any(Specification.class), eq(pr))).thenAnswer(inv -> {
            Specification<Cliente> spec = inv.getArgument(0);
            Root<Cliente> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb);
            return new PageImpl<>(Collections.emptyList());
        });
        service.consultar(req);
    }

    @Test
    void consultar_conFiltrosNull_cubreBranchFalse() {
        com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest req =
                new com.proyecto.servicios.onboarding.dto.request.ClienteConsultaRequest();
        // todos null por default
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(clienteRepository.findAll(any(Specification.class), eq(pr))).thenAnswer(inv -> {
            Specification<Cliente> spec = inv.getArgument(0);
            Root<Cliente> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb);
            return new PageImpl<>(Collections.emptyList());
        });
        service.consultar(req);
    }

    @Test
    void actualizarParcial_todosLosCamposNull_cubreBranchFalse() {
        Cliente c = Cliente.builder().id(1L).nombre("Original").correo("x@x.mx").build();
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aResponse(any())).thenReturn(mock(ClienteResponse.class));

        ClienteUpdateRequest r = new ClienteUpdateRequest();
        r.setId(1L); // solo id, todo lo demas null

        service.actualizarParcial(r);
        // nombre original no cambia
        assertThat(c.getNombre()).isEqualTo("Original");
    }

    @Test
    void darDeBaja_sinUsuario_noFalla() {
        Cliente c = Cliente.builder().id(1L).activo(Boolean.TRUE).build();
        c.setCuentas(new ArrayList<>());
        c.setUsuario(null);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        ClienteIdRequest req = new ClienteIdRequest();
        req.setId(1L);
        service.darDeBaja(req);
        assertThat(c.getActivo()).isFalse();
    }

}
