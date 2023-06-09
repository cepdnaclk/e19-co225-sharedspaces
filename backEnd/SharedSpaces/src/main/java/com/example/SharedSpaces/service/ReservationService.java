package com.example.SharedSpaces.service;

import com.example.SharedSpaces.controller.RequestResponse.Slot;
import com.example.SharedSpaces.controller.RequestResponse.ReservationRequest;
import com.example.SharedSpaces.controller.RequestResponse.ReservationResponse;
import com.example.SharedSpaces.db.*;
import com.example.SharedSpaces.exception.AllReadyReservedException;
import com.example.SharedSpaces.exception.EmailException;
import com.example.SharedSpaces.exception.InvalidDataException;
import com.example.SharedSpaces.models.Reservation;
import com.example.SharedSpaces.models.ResponsiblePerson;
import com.example.SharedSpaces.models.User;
import com.example.SharedSpaces.models.Waiting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ReservationService {

    // Declare dependencies
    private final ReservationDB reservationDB;
    private final WaitingDB waitingDB;
    private final UserDB userDB;
    private final ResponsiblePersonDB responsiblePersonDB;
    private final EmailService emailService;
    private final SpaceDB spaceDB;
    private final AdminDB adminDB;

    // Constructor injection
    @Autowired
    public ReservationService(ReservationDB reservationDB, UserDB userDB, WaitingDB waitingDB,
            ResponsiblePersonDB responsiblePersonDB, EmailService emailService,
            SpaceDB spaceDB, AdminDB adminDB) {
        this.reservationDB = reservationDB;
        this.userDB = userDB;
        this.waitingDB = waitingDB;
        this.responsiblePersonDB = responsiblePersonDB;
        this.emailService = emailService;
        this.spaceDB = spaceDB;
        this.adminDB = adminDB;
    }

    // Get all reservations
    public List<ReservationResponse> getAllResevations() {
        List<ReservationResponse> reservationResponses = new ArrayList<>();
        List<Reservation> reservations = reservationDB.getAllResevation();

        try {
            reservations.sort(Comparator.comparing(Reservation::getStartDateTime));
        } catch (Exception e) {
            System.out.println(e);
        }

        for (Reservation reservation : reservations) {
            reservationResponses.add(reservationToRequest(reservation));
        }

        return reservationResponses;
    }

    // Handle reservation request
    public ReservationResponse hadleReservation(ReservationRequest reservationRequest)
            throws AllReadyReservedException, EmailException, InvalidDataException {

        Reservation reservation = requestToReservation(reservationRequest);

        if (reservationRequest.getWaitingId() > 0) {
            try {
                waitingDB.deleteWaiting((long) reservationRequest.getWaitingId());
            } catch (Exception e) {
                throw new InvalidDataException("invalid user");
            }
        }

        User user = userDB.getUserById(reservation.getReservedById()).get();
        if (user == null) {
            throw new InvalidDataException("invalid user");
        }

        ResponsiblePerson responsiblePerson = responsiblePersonDB
                .getResponsiblePersonById(reservation.getResponsiblePersonId()).get();

        if (reservationDB.getReservationsByDetails(reservation.getSpaceID(), reservation.getStartDateTime(),
                reservation.getEndDateTime()) == null) {
            reservationDB.createReservation(reservation);
        } else {
            throw new AllReadyReservedException("Reserved");
        }

        ReservationResponse reservationResponse;
        String spaceName = spaceDB.getSpaceById((long) reservation.getSpaceID()).get().getName();
        if (responsiblePersonDB.getResponsiblePersonById(reservation.getReservedById()).isPresent()) {

            ResponsiblePerson Reuser = responsiblePersonDB.getResponsiblePersonById(reservation.getReservedById())
                    .get();
            reservationResponse = reservationToRequest(reservation, Reuser.fullName(), responsiblePerson.fullName());

            try {
                emailService.sendReservationNotificationResponsible(responsiblePerson, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime());
                emailService.sendReservationNotificationUSer(Reuser, responsiblePerson, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime());
            } catch (Exception e) {
                throw new EmailException("emailError");
            }

        } else {

            reservationResponse = reservationToRequest(reservation, user.getFullName(), responsiblePerson.fullName());

            try {
                emailService.sendReservationNotificationResponsible(user, responsiblePerson, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime());
                emailService.sendReservationNotificationUSer(user, responsiblePerson, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime());
            } catch (Exception e) {
                throw new EmailException("emailError");
            }

        }

        return reservationResponse;
    }

    public String reservationDeleteBySlot(Slot slot, String email) throws InvalidDataException, EmailException {
        Optional<Reservation> optional = reservationDB.getReservationByDetails(slot.getSpaceID(),
                slot.getStartDateTime(), slot.getEndDateTime());
        boolean admin = adminDB.getAdminByEmail(email).isPresent();

        if (optional.isEmpty())
            throw new InvalidDataException("invalid");

        Reservation reservation = optional.get();

        if (!email.equals(userDB.getUserById(reservation.getReservedById()).get().getEmail())
                && !email.equals(userDB.getUserById(reservation.getResponsiblePersonId()).get().getEmail()) && !admin)
            throw new InvalidDataException("invalid");

        reservationDB.deleteReservation(reservation.getId());

        String spaceName = spaceDB.getSpaceById((long) reservation.getSpaceID()).get().getName();
        List<Waiting> waitingList = waitingDB.getWaitingByDetails(slot.getSpaceID(), slot.getStartDateTime(),
                slot.getEndDateTime());

        if (!admin && waitingList != null) {

            try {
                waitingList.sort(Comparator.comparing(Waiting::getReservationDateTime));
            } catch (Exception e) {
                System.out.println(e);
            }

            Waiting waiting = waitingList.get(0);

            User waitingUser = userDB.getUserById(waiting.getReservedById()).get();

            if (reservationDB.getReservationsByDetails(waiting.getSpaceID(), waiting.getStartDateTime(),
                    waiting.getEndDateTime()) == null) {

                waiting.setAvailable(true);
                waitingDB.updateWaiting(waiting.getId(), waiting);

                try {
                    emailService.sendFreeReservationNotification(waitingUser, spaceName, waiting.getStartDateTime(),
                            waiting.getEndDateTime());
                } catch (Exception e) {
                    throw new EmailException("emailError");
                }
            }
        }

        User reservedUser = userDB.getUserById(reservation.getReservedById()).get();

        if (responsiblePersonDB.getResponsiblePersonByEmail(email).isPresent()) {
            ResponsiblePerson deletePerson = responsiblePersonDB.getResponsiblePersonByEmail(email).get();
            try {
                emailService.sendDelecteeReservationNotification(reservedUser, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime(), deletePerson.fullName());
            } catch (Exception e) {
                throw new EmailException("emailError");
            }

        } else if (adminDB.getAdminByEmail(email).isPresent()) {
            try {
                emailService.sendDelecteeReservationNotification(reservedUser, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime(), "Admin");
            } catch (Exception e) {
                throw new EmailException("emailError");
            }
        }

        else {
            User deletePerson = userDB.getUserByEmail(email).get();
            try {
                emailService.sendDelecteeReservationNotification(reservedUser, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime(), deletePerson.getFullName());
            } catch (Exception e) {
                throw new EmailException("emailError");
            }

        }

        return "Deleted";
    }

    public String reservationDeleteBySlot(int id, String email) throws InvalidDataException, EmailException {
        Optional<Reservation> optional = reservationDB.getReservationById((long) id);
        boolean admin = adminDB.getAdminByEmail(email).isPresent();

        if (optional.isEmpty())
            throw new InvalidDataException("invalid");

        Reservation reservation = optional.get();
        Slot slot = new Slot(reservation.getSpaceID(), reservation.getStartDateTime(), reservation.getEndDateTime());

        if (!email.equals(userDB.getUserById(reservation.getReservedById()).get().getEmail())
                && !email.equals(userDB.getUserById(reservation.getResponsiblePersonId()).get().getEmail()) && !admin)
            throw new InvalidDataException("invalid");

        reservationDB.deleteReservation(reservation.getId());

        String spaceName = spaceDB.getSpaceById((long) reservation.getSpaceID()).get().getName();

        List<Waiting> waitingList = waitingDB.getWaitingByDetails(slot.getSpaceID(), slot.getStartDateTime(),
                slot.getEndDateTime());

        if (!admin && waitingList != null) {

            try {
                waitingList.sort(Comparator.comparing(Waiting::getReservationDateTime));
            } catch (Exception e) {
                System.out.println(e);
            }

            Waiting waiting = waitingList.get(0);

            User waitingUser = userDB.getUserById(waiting.getReservedById()).get();

            if (reservationDB.getReservationsByDetails(waiting.getSpaceID(), waiting.getStartDateTime(),
                    waiting.getEndDateTime()) == null) {

                waiting.setAvailable(true);
                waitingDB.updateWaiting(waiting.getId(), waiting);

                try {
                    emailService.sendFreeReservationNotification(waitingUser, spaceName, waiting.getStartDateTime(),
                            waiting.getEndDateTime());
                } catch (Exception e) {
                    throw new EmailException("emailError");
                }
            }
        }

        User reservedUser = userDB.getUserById(reservation.getReservedById()).get();

        if (responsiblePersonDB.getResponsiblePersonByEmail(email).isPresent()) {
            ResponsiblePerson deletePerson = responsiblePersonDB.getResponsiblePersonByEmail(email).get();
            try {
                emailService.sendDelecteeReservationNotification(reservedUser, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime(), deletePerson.fullName());
            } catch (Exception e) {
                throw new EmailException("emailError");
            }

        } else if (adminDB.getAdminByEmail(email).isPresent()) {
            try {
                emailService.sendDelecteeReservationNotification(reservedUser, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime(), "Admin");
            } catch (Exception e) {
                throw new EmailException("emailError");
            }
        }

        else {
            User deletePerson = userDB.getUserByEmail(email).get();
            try {
                emailService.sendDelecteeReservationNotification(reservedUser, spaceName,
                        reservation.getStartDateTime(), reservation.getEndDateTime(), deletePerson.getFullName());
            } catch (Exception e) {
                throw new EmailException("emailError");
            }

        }

        return "Deleted";
    }

    public List<ReservationResponse> getUserReservationList(String email) {

        List<ReservationResponse> respons = new ArrayList<>();

        for (Reservation waiting : reservationDB.getAllResevation(email)) {
            respons.add(reservationToRequest(waiting));
        }

        return respons;
    }

    public List<ReservationResponse> getResponsibleReservationList(String email) {

        List<ReservationResponse> respons = new ArrayList<>();

        for (Reservation reservation : reservationDB.getAllResponsibleReservation(email)) {
            respons.add(reservationToRequest(reservation));
        }

        return respons;
    }

    public ReservationResponse reservationToRequest(Reservation reservation, String user, String responsible) {
        ReservationResponse reservationResponse = new ReservationResponse();

        reservationResponse.setId(reservation.getId());
        reservationResponse.setSpaceId(reservation.getSpaceID());
        reservationResponse.setTitle(reservation.getTitle());

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        reservationResponse.setDate(simpleDateFormat.format(reservation.getStartDateTime()));

        reservationResponse.setStartTime(
                reservation.getStartDateTime().getHours() * 100 + reservation.getStartDateTime().getMinutes());
        reservationResponse
                .setEndTime(reservation.getEndDateTime().getHours() * 100 + reservation.getEndDateTime().getMinutes());

        reservationResponse.setReservedBy(user);

        reservationResponse.setResponsiblePerson(responsible);
        reservationResponse.setResponsiblePersonId(reservation.getResponsiblePersonId());
        reservationResponse.setSpaceName(spaceDB.getSpaceById((long) reservation.getSpaceID()).get().getName());

        return reservationResponse;
    }

    public ReservationResponse reservationToRequest(Reservation reservation) {
        ReservationResponse reservationResponse = new ReservationResponse();

        reservationResponse.setId(reservation.getId());
        reservationResponse.setSpaceId(reservation.getSpaceID());
        reservationResponse.setTitle(reservation.getTitle());

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        reservationResponse.setDate(simpleDateFormat.format(reservation.getStartDateTime()));

        reservationResponse.setStartTime(
                reservation.getStartDateTime().getHours() * 100 + reservation.getStartDateTime().getMinutes());
        reservationResponse
                .setEndTime(reservation.getEndDateTime().getHours() * 100 + reservation.getEndDateTime().getMinutes());

        if (responsiblePersonDB.getResponsiblePersonById(reservation.getReservedById()).isPresent()) {
            reservationResponse.setReservedBy(
                    responsiblePersonDB.getResponsiblePersonById(reservation.getReservedById()).get().fullName());
        } else
            reservationResponse.setReservedBy(userDB.getUserById(reservation.getReservedById()).get().getFullName());

        reservationResponse.setResponsiblePerson(
                responsiblePersonDB.getResponsiblePersonById(reservation.getResponsiblePersonId()).get().fullName());

        reservationResponse.setResponsiblePersonId(reservation.getResponsiblePersonId());
        reservationResponse.setSpaceName(spaceDB.getSpaceById((long) reservation.getSpaceID()).get().getName());

        return reservationResponse;
    }

    public Reservation requestToReservation(ReservationRequest reservationRequest) {
        Reservation reservation = new Reservation();

        reservation.setTitle(reservationRequest.getTitle());
        reservation.setReservationDateTime(new Date(Long.parseLong(reservationRequest.getReservationDateTime())));
        reservation.setSpaceID(reservationRequest.getSpaceID());

        try {
            reservation.setStartDateTime(new SimpleDateFormat("yyyy-MM-dd").parse(reservationRequest.getDate()));
            reservation.setEndDateTime(new SimpleDateFormat("yyyy-MM-dd").parse(reservationRequest.getDate()));
        } catch (Exception e) {
            System.out.println(e);
        }

        reservation.getStartDateTime().setHours(reservationRequest.getStartTime() / 100);
        reservation.getStartDateTime().setMinutes(reservationRequest.getStartTime() % 100);
        reservation.getEndDateTime().setHours(reservationRequest.getEndTime() / 100);
        reservation.getEndDateTime().setMinutes(reservationRequest.getEndTime() % 100);

        reservation.setReservedById(reservationRequest.getReservedBy());
        reservation.setResponsiblePersonId(reservationRequest.getResponsiblePerson());

        return reservation;
    }

}
