package com.a205.mafya.api.service;

import com.a205.mafya.api.response.AttendanceTeamRes;
import com.a205.mafya.db.dto.Date;
import com.a205.mafya.db.entity.Attendance;
import com.a205.mafya.db.entity.User;
import com.a205.mafya.db.repository.AttendanceRepository;
import com.a205.mafya.db.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceServiceImpl implements AttendanceService {
    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    private String getTime() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String time = now.format(formatter);

        return (time);
    }

    public Date getDate() {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Date date = new Date();

        date.setYear(now.getYear() + "");
        date.setMonth(now.getMonthValue() < 10 ? "0" + now.getMonthValue() : now.getMonthValue() + "");
        date.setDay(now.getDayOfMonth() < 10 ? "0" + now.getDayOfMonth() : now.getDayOfMonth() + "");

        return (date);
    }

    @Transactional
    int recordEntrance(String userCode) {
        Optional<User> user = userRepository.findByUserCode(userCode);

        if (user.isPresent()) {
            Date date = getDate();
            Optional<Attendance> attendance = attendanceRepository.findByUserAndDayAndMonthAndYear(user.get(), date.getDay(), date.getMonth(), date.getYear());
            if (attendance.isPresent()) return (RE_REQUEST);

            Attendance data = new Attendance();

            data.setType(ENTRANCE);

            data.setEnterTime(getTime());
            data.setExitTime("");

            data.setYear(date.getYear());
            data.setMonth(date.getMonth());
            data.setDay(date.getDay());

            data.setUser(user.get());

            attendanceRepository.save(data);

            return (data.getType());
        }
        else
            return (NO_USER);
    }

    @Transactional
    int recordExit(String userCode) {
        Optional<User> user = userRepository.findByUserCode(userCode);

        if (user.isPresent()) {
            Date date = getDate();
            Optional<Attendance> attendance = attendanceRepository.findByUserAndDayAndMonthAndYear(user.get(), date.getDay(), date.getMonth(), date.getYear());

            if (!attendance.isPresent()) {  //지각
                Attendance data = new Attendance();

                data.setType(TRADY);

                data.setEnterTime(getTime());
                data.setExitTime("");

                data.setYear(date.getYear());
                data.setMonth(date.getMonth());
                data.setDay(date.getDay());

                data.setUser(user.get());

                attendanceRepository.save(data);
                return (data.getType());
            }
            else {
                LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));

                if (now.getHour() < EXITTIME) {
                    if (attendance.get().getType() / 10 == 1)   attendance.get().setType(TRADY_AND_EARLYLEAVE);
                    else                                        attendance.get().setType(ENTRANCE_AND_EARLYLEAVE);
                }
                else {
                    if (attendance.get().getType() / 10 == 1)   attendance.get().setType(TRADY_AND_NORMALEXIT);
                    else                                        attendance.get().setType(ENTRANCE_AND_NORMALEXIT);
                }
                attendance.get().setExitTime(getTime());

                attendanceRepository.save(attendance.get());

                return (attendance.get().getType());
            }

        }
        else
            return (NO_USER);
    }


    /**
     *
     * @param userCode
     * @return
     * -1: 존재 하지 않는 유저
     * 0: 입실
     * 10: 지각
     * 11: 지각 한 후 조퇴
     * 12: 지각 한 후 정상 퇴실
     * 2: 정상 입실 후 조퇴
     * 3: 정상 퇴실
     * 4: 입실 후 다시 입실 요청(요청 안 받고, 지각 시간 이후부터 다시 체크 가능)
     * 99: 결석
     */
    @Override
    public int record(String userCode) {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        int time = now.getHour();
        int result;

        if (time < ENTERTIME)    result = recordEntrance(userCode);
        else                    result = recordExit(userCode);

        return (result);
    }

    @Override
    @Transactional
    public List<AttendanceTeamRes> getTeamInfo(String teamCode) {
        List<AttendanceTeamRes> attendanceTeamResList = new LinkedList<>();

        List<User> userList = userRepository.findAllByTeamCode(teamCode);
        for (User user : userList) {
            Date date = getDate();
            Optional<Attendance> attendance = attendanceRepository.findByUserAndDayAndMonthAndYear(user, date.getDay(), date.getMonth(), date.getYear());

            AttendanceTeamRes attendanceTeamRes = new AttendanceTeamRes();

            attendanceTeamRes.setId(user.getId());
            attendanceTeamRes.setName(user.getName());
            attendanceTeamRes.setStatus(user.getStatus());
            attendanceTeamRes.setTeamCode(user.getTeamCode());
            attendanceTeamRes.setClassCode(user.getClassCode());
            attendanceTeamRes.setPhoneNum(user.getPhoneNum());
            attendanceTeamRes.setTeamLeader(user.isTeamLeader());

            if (attendance.isPresent()) attendanceTeamRes.setAttendanceStatus(attendance.get().getType());
            else                        attendanceTeamRes.setAttendanceStatus(ABSENT);

            attendanceTeamResList.add(attendanceTeamRes);
        }
        return (attendanceTeamResList);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 31 18 * * 1-5", zone = "Asia/Seoul")   //평일 저녁 6시 31분 (퇴실 미체크도 결석으로 처리)
    public void processAbsentScheduler() {
        System.out.println("[processAbsentScheduler]");

        List<User> userList = userRepository.findAll();
        Date date = getDate();

        for (int i = 0; i < userList.size(); i++) {
            Optional<Attendance> status = attendanceRepository.findByUserAndDayAndMonthAndYear(userList.get(i), date.getDay(), date.getMonth(), date.getYear());
            Attendance attendance = new Attendance();

             if (status.isPresent()) {   //퇴실 체크 했음
                int type = status.get().getType();

                if (type == TRADY_AND_EARLYLEAVE || type == TRADY_AND_NORMALEXIT || type == ENTRANCE_AND_EARLYLEAVE || type == ENTRANCE_AND_NORMALEXIT)
                    continue;
            }

            //User 정보에 결석 1일 추가
            userList.get(i).setAbsent(userList.get(i).getAbsent() + 1);
            userRepository.save(userList.get(i));

            //출석 기록에 결석 추가
            if (status.isPresent()) {   //기존 데이터 변경
                status.get().setType(ABSENT);

                attendanceRepository.save(status.get());
            }
            else {      //새롭게 데이터 추가
                attendance.setType(ABSENT);
                attendance.setYear(date.getYear());
                attendance.setMonth(date.getMonth());
                attendance.setDay(date.getDay());
                attendance.setUser(userList.get(i));
                attendance.setExitTime("");
                attendance.setEnterTime("");

                attendanceRepository.save(attendance);
            }
        }
    }

    @Override
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")    //매달 1일 0시에 실행
    public void processTradyInitScheduler() {
        System.out.println("[processTradyInitScheduler]");

        List<User> userList = userRepository.findAll();

        for (int i = 0; i < userList.size(); i++) {
            userList.get(i).setTardy(0);
            userRepository.save(userList.get(i));
        }
    }

    @Override
    @Scheduled(cron = "0 31 18 * * 1-5", zone = "Asia/Seoul") //평일 저녁 6시 31분
    public void processTradyScheduler() {
        System.out.println("[processTradyScheduler]");

        List<User> userList = userRepository.findAll();
        Date date = getDate();

        for (int i = 0; i < userList.size(); i++) {
            Optional<Attendance> status = attendanceRepository.findByUserAndDayAndMonthAndYear(userList.get(i), date.getDay(), date.getMonth(), date.getYear());
            int type;

            if (!status.isPresent()) continue;  //결석 - 결석 처리는 따로 처리
            type = status.get().getType();

            if (!(type == TRADY_AND_EARLYLEAVE || type == TRADY_AND_NORMALEXIT || type == ENTRANCE_AND_EARLYLEAVE)) continue;

            userList.get(i).setTardy(userList.get(i).getTardy() + 1);       //지각 1회 증가
            if(userList.get(i).getTardy() == TRANSFORM_ABSENT) {            //지각 횟수가 결석으로 충족되면 지각 횟수는 0으로 결석 횟수는 1회 증가
                userList.get(i).setAbsent(userList.get(i).getAbsent() + 1);
                userList.get(i).setTardy(0);
            }
            userRepository.save(userList.get(i));
        }
    }
}